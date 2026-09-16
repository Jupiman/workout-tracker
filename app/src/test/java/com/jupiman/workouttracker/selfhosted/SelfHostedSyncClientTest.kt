package com.jupiman.workouttracker.selfhosted

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.SSLException
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SelfHostedSyncClientTest {
    @Test
    fun testConnectionUsesTheExplicitProtocolWithoutFallback() = runTest {
        val opened = mutableListOf<URL>()
        val client = HttpSelfHostedSyncClient { url ->
            opened += url
            SuccessfulInfoConnection(url)
        }

        assertEquals(
            ConnectionTestResult.Success("1.0.0"),
            client.testConnection(SelfHostedConfiguration("192.168.1.140:5544", false, "token")),
        )
        assertEquals(
            ConnectionTestResult.Success("1.0.0"),
            client.testConnection(SelfHostedConfiguration("workout.example.com", true, "token")),
        )

        assertEquals(
            listOf(
                "http://192.168.1.140:5544/api/v1/info",
                "https://workout.example.com/api/v1/info",
            ),
            opened.map(URL::toString),
        )
    }

    @Test
    fun tlsErrorsAreReportedOnlyForHttpsConfiguration() = runTest {
        val client = HttpSelfHostedSyncClient(::FailingConnection)

        assertEquals(
            ConnectionTestResult.TlsFailure(),
            client.testConnection(SelfHostedConfiguration("workout.example.com", true, "token")),
        )
        assertEquals(
            ConnectionTestResult.Unreachable(),
            client.testConnection(SelfHostedConfiguration("192.168.1.140:5544", false, "token")),
        )
    }

    @Test
    fun createdBatchItemIsSuccessfulAndRequestHasOnlyWorkoutsAtTheTopLevel() = runTest {
        val exchange = uploadBatch(
            response = """{"results":[{"syncId":"one","status":"CREATED"}]}""",
            syncIds = listOf("one"),
        )

        assertEquals(
            UploadResult.Processed(listOf(RecordUploadResult("one", true))),
            exchange.result,
        )
        val request = JSONObject(exchange.requestBody)
        assertFalse(request.has("schemaVersion"))
        assertEquals(1, request.length())
        assertEquals("one", request.getJSONArray("workouts").getJSONObject(0).getString("syncId"))
    }

    @Test
    fun updatedBatchItemIsSuccessful() = runTest {
        val exchange = uploadBatch(
            response = """{"results":[{"syncId":"one","status":"UPDATED"}]}""",
            syncIds = listOf("one"),
        )

        assertEquals(
            UploadResult.Processed(listOf(RecordUploadResult("one", true))),
            exchange.result,
        )
    }

    @Test
    fun rejectedBatchItemPreservesMessageAndDetailsAsPermanentRecordFailure() = runTest {
        val exchange = uploadBatch(
            response = """
                {
                  "results":[{
                    "syncId":"one",
                    "status":"REJECTED",
                    "message":"Workout was rejected.",
                    "details":{"field":"completedAt"}
                  }]
                }
            """.trimIndent(),
            syncIds = listOf("one"),
        )

        assertEquals(
            UploadResult.Processed(
                listOf(
                    RecordUploadResult(
                        syncId = "one",
                        success = false,
                        code = "REJECTED",
                        message = "Workout was rejected. Details: {\"field\":\"completedAt\"}",
                    ),
                ),
            ),
            exchange.result,
        )
    }

    @Test
    fun mixedBatchKeepsSuccessfulAndRejectedResultsSeparate() = runTest {
        val exchange = uploadBatch(
            response = """
                {"results":[
                  {"syncId":"one","status":"CREATED"},
                  {"syncId":"two","status":"UPDATED"},
                  {"syncId":"three","status":"REJECTED","message":"Invalid payload"}
                ]}
            """.trimIndent(),
            syncIds = listOf("one", "two", "three"),
        )

        assertEquals(
            UploadResult.Processed(
                listOf(
                    RecordUploadResult("one", true),
                    RecordUploadResult("two", true),
                    RecordUploadResult("three", false, "REJECTED", "Invalid payload"),
                ),
            ),
            exchange.result,
        )
    }

    @Test
    fun malformedBatchResponseRemainsUnexpected() = runTest {
        val exchange = uploadBatch(
            response = """{"results":[{"syncId":"one","status":"UNKNOWN"}]}""",
            syncIds = listOf("one"),
        )

        assertEquals(
            UploadResult.Unexpected("Server returned an invalid batch response."),
            exchange.result,
        )
    }

    private suspend fun uploadBatch(response: String, syncIds: List<String>): BatchExchange {
        lateinit var connection: BatchConnection
        val client = HttpSelfHostedSyncClient { url ->
            BatchConnection(url, response).also { connection = it }
        }
        val result = client.uploadBatch(
            SelfHostedConfiguration("workout.example.com", true, "token"),
            syncIds.map { syncId ->
                SelfHostedWorkoutPayload(
                    syncId,
                    JSONObject().put("schemaVersion", PAYLOAD_SCHEMA_VERSION).put("syncId", syncId),
                )
            },
        )
        return BatchExchange(result, connection.requestBody.toString(Charsets.UTF_8.name()))
    }

    private data class BatchExchange(val result: UploadResult, val requestBody: String)

    private class SuccessfulInfoConnection(url: URL) : HttpURLConnection(url) {
        private val response = """
            {
              "serverVersion":"1.0.0",
              "apiVersion":1,
              "minimumPayloadSchemaVersion":1,
              "maximumPayloadSchemaVersion":1
            }
        """.trimIndent().toByteArray()

        override fun getResponseCode(): Int = 200
        override fun getInputStream() = ByteArrayInputStream(response)
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }

    private class FailingConnection(url: URL) : HttpURLConnection(url) {
        override fun getResponseCode(): Int = throw SSLException("handshake failed")
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }

    private class BatchConnection(url: URL, response: String) : HttpURLConnection(url) {
        val requestBody = ByteArrayOutputStream()
        private val responseBody = response.toByteArray()

        override fun getOutputStream() = requestBody
        override fun getResponseCode(): Int = 200
        override fun getInputStream() = ByteArrayInputStream(responseBody)
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun connect() = Unit
    }
}
