package com.jupiman.workouttracker.selfhosted

import com.jupiman.workouttracker.data.local.entity.SelfHostedSyncState
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails

data class SelfHostedSettingsState(
    val enabled: Boolean,
    val serverUrl: String,
    val hasToken: Boolean,
    val lastSuccessfulSyncAt: Long?,
    val latestError: String?,
    val pendingUploads: Int,
    val permanentFailures: Int,
)

data class SelfHostedSyncSummary(val synchronized: Int, val failed: Int, val retryNeeded: Boolean)

class SelfHostedSyncManager(
    private val client: SelfHostedSyncClient,
    private val workoutStore: SelfHostedWorkoutStore,
    private val settingsStore: SelfHostedSettingsStore,
    private val tokenStore: SelfHostedTokenStore,
    private val scheduler: SelfHostedWorkoutScheduler,
    private val allowLocalHttp: Boolean,
) {
    suspend fun settingsState(): SelfHostedSettingsState {
        val preferences = settingsStore.current()
        return SelfHostedSettingsState(
            enabled = preferences.enabled,
            serverUrl = preferences.serverUrl,
            hasToken = tokenStore.hasToken(),
            lastSuccessfulSyncAt = preferences.lastSuccessfulSyncAt,
            latestError = preferences.lastError,
            pendingUploads = workoutStore.pendingCount(),
            permanentFailures = workoutStore.permanentFailureCount(),
        )
    }

    suspend fun schedulePendingIfConfigured() {
        val preferences = settingsStore.current()
        if (preferences.enabled && configurationOrNull(recordError = false) != null &&
            workoutStore.pendingCount() > 0
        ) {
            scheduler.schedulePending()
        }
    }

    suspend fun scheduleFullAfterRestore() {
        val preferences = settingsStore.current()
        if (preferences.enabled && configurationOrNull(recordError = false) != null) {
            scheduler.scheduleFullSync()
        }
    }

    suspend fun saveConfiguration(serverUrl: String, replacementToken: String?): Result<String> = runCatching {
        val normalized = when (val validation = validateServerUrl(serverUrl, allowLocalHttp)) {
            is ServerUrlValidation.Invalid -> error(validation.message)
            is ServerUrlValidation.Valid -> validation.normalizedUrl
        }
        replacementToken?.trim()?.takeIf { it.isNotEmpty() }?.let(tokenStore::setToken)
        settingsStore.setServerUrl(normalized)
        settingsStore.setLastError(null)
        if (settingsStore.current().enabled) scheduler.scheduleFullSync()
        normalized
    }

    suspend fun setEnabled(enabled: Boolean): Result<Unit> = runCatching {
        if (enabled) {
            requireNotNull(configurationOrNull(recordError = false)) {
                "Enter a valid server URL and API token first."
            }
            settingsStore.setEnabled(true)
            settingsStore.setLastError(null)
            scheduler.scheduleFullSync()
        } else {
            settingsStore.setEnabled(false)
            scheduler.cancel()
        }
    }

    suspend fun testConnection(serverUrl: String, token: String?): ConnectionTestResult {
        val normalized = when (val validation = validateServerUrl(serverUrl, allowLocalHttp)) {
            is ServerUrlValidation.Invalid -> return ConnectionTestResult.InvalidUrl(validation.message)
            is ServerUrlValidation.Valid -> validation.normalizedUrl
        }
        val effectiveToken = token?.trim()?.takeIf { it.isNotEmpty() } ?: tokenStore.getToken()
        if (effectiveToken.isNullOrBlank()) return ConnectionTestResult.Unauthorized
        return client.testConnection(SelfHostedConfiguration(normalized, effectiveToken))
    }

    suspend fun syncPending(): SelfHostedSyncSummary {
        if (!settingsStore.current().enabled) return SelfHostedSyncSummary(0, 0, false)
        val configuration = configurationOrNull(recordError = true) ?: return SelfHostedSyncSummary(0, 0, false)
        compatibilityFailure(configuration)?.let { return it }
        var successful = 0
        var failed = 0
        while (true) {
            val batch = workoutStore.pending(SELF_HOSTED_BATCH_SIZE)
            if (batch.isEmpty()) break
            val result = upload(configuration, batch)
            successful += result.synchronized
            failed += result.failed
            if (result.retryNeeded || result.stop) {
                return SelfHostedSyncSummary(successful, failed, result.retryNeeded)
            }
        }
        recordRunCompletion(failed)
        return SelfHostedSyncSummary(successful, failed, false)
    }

    suspend fun syncAll(): SelfHostedSyncSummary {
        val configuration = configurationOrNull(recordError = true) ?: return SelfHostedSyncSummary(0, 0, false)
        compatibilityFailure(configuration)?.let { return it }
        var offset = 0
        var successful = 0
        var failed = 0
        while (true) {
            val batch = workoutStore.finalized(SELF_HOSTED_BATCH_SIZE, offset)
            if (batch.isEmpty()) break
            val result = upload(configuration, batch)
            successful += result.synchronized
            failed += result.failed
            if (result.retryNeeded || result.stop) {
                return SelfHostedSyncSummary(successful, failed, result.retryNeeded)
            }
            offset += batch.size
        }
        recordRunCompletion(failed)
        return SelfHostedSyncSummary(successful, failed, false)
    }

    suspend fun syncAllIfEnabled(): SelfHostedSyncSummary {
        if (!settingsStore.current().enabled) return SelfHostedSyncSummary(0, 0, false)
        return syncAll()
    }

    private suspend fun compatibilityFailure(configuration: SelfHostedConfiguration): SelfHostedSyncSummary? =
        when (val test = client.testConnection(configuration)) {
            is ConnectionTestResult.Success -> null
            is ConnectionTestResult.Unreachable -> globalFailure(test.message, retry = true)
            is ConnectionTestResult.TlsFailure -> globalFailure(test.message, retry = true)
            is ConnectionTestResult.InvalidUrl -> globalFailure(test.message, retry = false)
            ConnectionTestResult.Unauthorized -> globalFailure("Authentication failed.", retry = false)
            ConnectionTestResult.Incompatible -> globalFailure("Server API is incompatible.", retry = false)
            is ConnectionTestResult.Unexpected -> globalFailure(test.message, retry = false)
        }

    private suspend fun globalFailure(message: String, retry: Boolean): SelfHostedSyncSummary {
        settingsStore.setLastError(sanitize(message))
        return SelfHostedSyncSummary(0, 0, retry)
    }

    private suspend fun upload(
        configuration: SelfHostedConfiguration,
        sessions: List<WorkoutSessionWithDetails>,
    ): UploadBatchResult {
        val now = System.currentTimeMillis()
        val mapped = mutableListOf<Pair<WorkoutSessionWithDetails, SelfHostedWorkoutPayload>>()
        var mappingFailures = 0
        sessions.forEach { details ->
            try {
                mapped += details to details.toSelfHostedPayload()
            } catch (_: Exception) {
                mappingFailures += 1
                workoutStore.updateState(
                    details.session.id,
                    SelfHostedSyncState.FAILED_PERMANENT,
                    now,
                    "Local workout snapshot is invalid.",
                )
            }
        }
        if (mapped.isEmpty()) return UploadBatchResult(0, mappingFailures, retryNeeded = false, stop = false)
        return when (val result = client.uploadBatch(configuration, mapped.map { it.second })) {
            is UploadResult.Processed -> {
                val bySyncId = mapped.associateBy { it.second.syncId }
                result.results.forEach { record ->
                    val session = bySyncId.getValue(record.syncId).first.session
                    workoutStore.updateState(
                        session.id,
                        if (record.success) SelfHostedSyncState.SYNCED else SelfHostedSyncState.FAILED_PERMANENT,
                        now,
                        if (record.success) null else sanitize(record.message ?: record.code ?: "Workout rejected."),
                    )
                }
                val succeeded = result.results.count { it.success }
                val recordFailures = result.results.size - succeeded
                UploadBatchResult(succeeded, mappingFailures + recordFailures, false, false)
            }
            is UploadResult.Retryable -> {
                mapped.forEach { (details, _) ->
                    workoutStore.updateState(
                        details.session.id,
                        SelfHostedSyncState.PENDING,
                        now,
                        sanitize(result.message),
                    )
                }
                settingsStore.setLastError(sanitize(result.message))
                UploadBatchResult(0, mappingFailures + mapped.size, true, true)
            }
            UploadResult.Unauthorized -> stopBatch(mapped, mappingFailures, "Authentication failed.")
            UploadResult.Incompatible -> stopBatch(mapped, mappingFailures, "Server API is incompatible.")
            is UploadResult.Permanent -> {
                mapped.forEach { (details, _) ->
                    workoutStore.updateState(
                        details.session.id,
                        SelfHostedSyncState.FAILED_PERMANENT,
                        now,
                        sanitize(result.message),
                    )
                }
                UploadBatchResult(0, mappingFailures + mapped.size, false, false)
            }
            is UploadResult.Unexpected -> stopBatch(mapped, mappingFailures, result.message)
        }
    }

    private suspend fun stopBatch(
        mapped: List<Pair<WorkoutSessionWithDetails, SelfHostedWorkoutPayload>>,
        mappingFailures: Int,
        message: String,
    ): UploadBatchResult {
        val sanitized = sanitize(message)
        mapped.forEach { (details, _) ->
            workoutStore.updateState(
                details.session.id,
                details.session.selfHostedSyncState,
                System.currentTimeMillis(),
                sanitized,
            )
        }
        settingsStore.setLastError(sanitized)
        return UploadBatchResult(0, mappingFailures + mapped.size, false, true)
    }

    private suspend fun configurationOrNull(recordError: Boolean): SelfHostedConfiguration? {
        val preferences = settingsStore.current()
        val normalized = when (val validation = validateServerUrl(preferences.serverUrl, allowLocalHttp)) {
            is ServerUrlValidation.Valid -> validation.normalizedUrl
            is ServerUrlValidation.Invalid -> {
                if (recordError) settingsStore.setLastError(validation.message)
                return null
            }
        }
        val token = tokenStore.getToken()
        if (token.isNullOrBlank()) {
            if (recordError) settingsStore.setLastError("API token is missing.")
            return null
        }
        return SelfHostedConfiguration(normalized, token)
    }

    private suspend fun recordSuccessfulRun() {
        settingsStore.setLastSuccessfulSyncAt(System.currentTimeMillis())
        settingsStore.setLastError(null)
    }

    private suspend fun recordRunCompletion(failed: Int) {
        if (failed == 0) {
            recordSuccessfulRun()
        } else {
            settingsStore.setLastError("$failed workout${if (failed == 1) "" else "s"} could not be synchronized.")
        }
    }

    private fun sanitize(message: String): String = message.replace(Regex("[\\r\\n]+"), " ").take(240)

    private data class UploadBatchResult(
        val synchronized: Int,
        val failed: Int,
        val retryNeeded: Boolean,
        val stop: Boolean,
    )
}
