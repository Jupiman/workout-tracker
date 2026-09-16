package com.jupiman.workouttracker.selfhosted

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed interface ConnectionTestResult {
    data class Success(val serverVersion: String) : ConnectionTestResult
    data class InvalidUrl(val message: String) : ConnectionTestResult
    data class Unreachable(val message: String = "Server is unreachable.") : ConnectionTestResult
    data class TlsFailure(val message: String = "TLS connection failed.") : ConnectionTestResult
    data object Unauthorized : ConnectionTestResult
    data object Incompatible : ConnectionTestResult
    data class Unexpected(val message: String = "Unexpected server response.") : ConnectionTestResult
}

data class RecordUploadResult(
    val syncId: String,
    val success: Boolean,
    val code: String? = null,
    val message: String? = null,
)

sealed interface UploadResult {
    data class Processed(val results: List<RecordUploadResult>) : UploadResult
    data class Retryable(val message: String) : UploadResult
    data object Unauthorized : UploadResult
    data object Incompatible : UploadResult
    data class Permanent(val message: String) : UploadResult
    data class Unexpected(val message: String) : UploadResult
}

interface SelfHostedSyncClient {
    suspend fun testConnection(configuration: SelfHostedConfiguration): ConnectionTestResult
    suspend fun uploadWorkout(
        configuration: SelfHostedConfiguration,
        workout: SelfHostedWorkoutPayload,
    ): UploadResult
    suspend fun uploadBatch(
        configuration: SelfHostedConfiguration,
        workouts: List<SelfHostedWorkoutPayload>,
    ): UploadResult
}

class HttpSelfHostedSyncClient(
    private val openConnection: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) : SelfHostedSyncClient {
    override suspend fun testConnection(configuration: SelfHostedConfiguration): ConnectionTestResult = withContext(Dispatchers.IO) {
        val response = request(configuration, "GET", "/api/v1/info", null)
        when (response) {
            is HttpResponse.Failure -> response.toConnectionResult()
            is HttpResponse.Value -> when {
                response.code == 401 -> ConnectionTestResult.Unauthorized
                response.code in 500..599 -> ConnectionTestResult.Unreachable("Server returned HTTP ${response.code}.")
                response.code !in 200..299 -> ConnectionTestResult.Unexpected("Server returned HTTP ${response.code}.")
                else -> runCatching {
                    val json = JSONObject(response.body)
                    val serverVersion = json.getString("serverVersion").takeIf { it.isNotBlank() }
                        ?: error("Missing server version")
                    val apiVersion = json.getInt("apiVersion")
                    val minimum = json.getInt("minimumPayloadSchemaVersion")
                    val maximum = json.getInt("maximumPayloadSchemaVersion")
                    if (apiVersion == 1 && compatiblePayloadSchema(minimum, maximum)) {
                        ConnectionTestResult.Success(serverVersion)
                    } else {
                        ConnectionTestResult.Incompatible
                    }
                }.getOrElse { ConnectionTestResult.Unexpected() }
            }
        }
    }

    override suspend fun uploadWorkout(
        configuration: SelfHostedConfiguration,
        workout: SelfHostedWorkoutPayload,
    ): UploadResult = withContext(Dispatchers.IO) {
        val response = request(configuration, "PUT", "/api/v1/workouts/${workout.syncId}", workout.json.toString())
        when (response) {
            is HttpResponse.Failure -> response.toUploadResult()
            is HttpResponse.Value -> classifySingleUpload(response, workout.syncId)
        }
    }

    override suspend fun uploadBatch(
        configuration: SelfHostedConfiguration,
        workouts: List<SelfHostedWorkoutPayload>,
    ): UploadResult = withContext(Dispatchers.IO) {
        require(workouts.isNotEmpty() && workouts.size <= SELF_HOSTED_BATCH_SIZE)
        val body = JSONObject()
            .put("workouts", JSONArray(workouts.map { it.json }))
            .toString()
        val response = request(configuration, "POST", "/api/v1/workouts/batch", body)
        when (response) {
            is HttpResponse.Failure -> response.toUploadResult()
            is HttpResponse.Value -> classifyBatchUpload(response, workouts.map { it.syncId }.toSet())
        }
    }

    private fun classifySingleUpload(response: HttpResponse.Value, syncId: String): UploadResult = when {
        response.code in 200..299 -> UploadResult.Processed(listOf(RecordUploadResult(syncId, true)))
        response.code == 401 -> UploadResult.Unauthorized
        response.code in 500..599 -> UploadResult.Retryable("Server returned HTTP ${response.code}.")
        response.code == 400 || response.code == 422 -> UploadResult.Permanent("Server rejected the workout.")
        else -> UploadResult.Unexpected("Server returned HTTP ${response.code}.")
    }

    private fun classifyBatchUpload(response: HttpResponse.Value, expectedIds: Set<String>): UploadResult = when {
        response.code == 401 -> UploadResult.Unauthorized
        response.code in 500..599 -> UploadResult.Retryable("Server returned HTTP ${response.code}.")
        response.code == 400 || response.code == 422 -> UploadResult.Permanent("Server rejected the workout batch.")
        response.code !in 200..299 -> UploadResult.Unexpected("Server returned HTTP ${response.code}.")
        else -> runCatching {
            val values = JSONObject(response.body).getJSONArray("results")
            val results = List(values.length()) { index ->
                values.getJSONObject(index).let { item ->
                    val status = item.getString("status")
                    val success = when (status) {
                        "CREATED", "UPDATED" -> true
                        "REJECTED" -> false
                        else -> error("Unknown batch result status")
                    }
                    RecordUploadResult(
                        syncId = item.getString("syncId"),
                        success = success,
                        code = item.optionalString("code") ?: status.takeIf { !success },
                        message = if (success) item.optionalString("message") else item.rejectionMessage(),
                    )
                }
            }
            if (results.map { it.syncId }.toSet() != expectedIds || results.size != expectedIds.size) {
                UploadResult.Unexpected("Server returned an incomplete batch result.")
            } else {
                UploadResult.Processed(results)
            }
        }.getOrElse { UploadResult.Unexpected("Server returned an invalid batch response.") }
    }

    private fun request(
        configuration: SelfHostedConfiguration,
        method: String,
        path: String,
        body: String?,
    ): HttpResponse {
        val connection = try {
            openConnection(URL(configuration.baseUrl + path))
        } catch (_: Exception) {
            return HttpResponse.Failure.InvalidUrl
        }
        return try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${configuration.apiToken}")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(8_192)
                while (result.length < MAX_RESPONSE_CHARACTERS) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, MAX_RESPONSE_CHARACTERS - result.length))
                    if (count < 0) break
                    result.append(buffer, 0, count)
                }
                result.toString()
            }.orEmpty()
            HttpResponse.Value(code, responseBody)
        } catch (_: SSLException) {
            if (configuration.useHttps) HttpResponse.Failure.Tls else HttpResponse.Failure.Network
        } catch (_: SocketTimeoutException) {
            HttpResponse.Failure.Network
        } catch (_: IOException) {
            HttpResponse.Failure.Network
        } catch (_: IllegalArgumentException) {
            HttpResponse.Failure.InvalidRequest
        } catch (_: SecurityException) {
            HttpResponse.Failure.Network
        } finally {
            connection.disconnect()
        }
    }

    private sealed interface HttpResponse {
        data class Value(val code: Int, val body: String) : HttpResponse
        sealed interface Failure : HttpResponse {
            data object InvalidUrl : Failure
            data object InvalidRequest : Failure
            data object Network : Failure
            data object Tls : Failure

            fun toConnectionResult(): ConnectionTestResult = when (this) {
                InvalidUrl -> ConnectionTestResult.InvalidUrl("The server URL is invalid.")
                InvalidRequest -> ConnectionTestResult.Unexpected("Request configuration is invalid.")
                Network -> ConnectionTestResult.Unreachable()
                Tls -> ConnectionTestResult.TlsFailure()
            }

            fun toUploadResult(): UploadResult = when (this) {
                InvalidUrl -> UploadResult.Unexpected("The server URL is invalid.")
                InvalidRequest -> UploadResult.Unexpected("Request configuration is invalid.")
                Network -> UploadResult.Retryable("Server is unreachable.")
                Tls -> UploadResult.Retryable("TLS connection failed.")
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 10_000
        const val READ_TIMEOUT_MILLIS = 20_000
        const val MAX_RESPONSE_CHARACTERS = 1_000_000
    }
}

private fun JSONObject.optionalString(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key).trim().takeIf { it.isNotEmpty() }

private fun JSONObject.rejectionMessage(): String? {
    val message = optionalString("message")
    val details = when (val value = opt("details")) {
        null, JSONObject.NULL -> null
        is String -> value.trim().takeIf { it.isNotEmpty() }
        else -> value.toString().takeIf { it.isNotBlank() }
    }
    return when {
        message != null && details != null -> "$message Details: $details"
        message != null -> message
        else -> details
    }
}
