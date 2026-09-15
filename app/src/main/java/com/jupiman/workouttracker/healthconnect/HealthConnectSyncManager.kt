package com.jupiman.workouttracker.healthconnect

import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class HealthConnectSyncManager(
    private val gateway: HealthConnectGateway,
    private val workoutSource: FinalizedWorkoutSource,
    private val isSyncEnabled: suspend () -> Boolean,
    private val postWorkoutScope: CoroutineScope,
) : FinalizedWorkoutSync {
    suspend fun settingsState(): HealthConnectSettingsState {
        val availability = runCatching { gateway.availability() }
            .getOrDefault(HealthConnectAvailability.UNAVAILABLE)
        val hasPermission = availability == HealthConnectAvailability.AVAILABLE &&
            runCatching { gateway.hasWriteExercisePermission() }.getOrDefault(false)
        return HealthConnectSettingsState(availability, hasPermission)
    }

    suspend fun syncAllFinalizedWorkouts(): HealthConnectSyncResult = syncRecords {
        workoutSource.finalizedWorkouts()
    }

    private suspend fun syncFinalizedWorkout(sessionId: Long): HealthConnectSyncResult = syncRecords {
        listOfNotNull(workoutSource.finalizedWorkout(sessionId))
    }

    private suspend fun syncRecords(
        loadSessions: suspend () -> List<WorkoutSessionEntity>,
    ): HealthConnectSyncResult {
        val state = settingsState()
        return when (state.availability) {
            HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED ->
                HealthConnectSyncResult.ProviderUpdateRequired
            HealthConnectAvailability.UNAVAILABLE ->
                HealthConnectSyncResult.Unavailable
            HealthConnectAvailability.AVAILABLE -> {
                if (!state.hasWritePermission) return HealthConnectSyncResult.PermissionRequired
                val records = runCatching {
                    loadSessions().mapNotNull { it.toHealthConnectWorkoutRecord() }
                }.getOrElse { return HealthConnectSyncResult.Failed }
                runCatching {
                    records.chunked(MAX_BATCH_SIZE).forEach { gateway.writeExerciseSessions(it) }
                }.fold(
                    onSuccess = { HealthConnectSyncResult.Completed(records.size) },
                    onFailure = { HealthConnectSyncResult.Failed },
                )
            }
        }
    }

    suspend fun syncIfEnabled(): HealthConnectSyncResult {
        if (!runCatching { isSyncEnabled() }.getOrDefault(false)) return HealthConnectSyncResult.Disabled
        return syncAllFinalizedWorkouts()
    }

    override fun syncAfterFinalization(sessionId: Long) {
        postWorkoutScope.launch {
            if (runCatching { isSyncEnabled() }.getOrDefault(false)) {
                syncFinalizedWorkout(sessionId)
            }
        }
    }

    private companion object {
        const val MAX_BATCH_SIZE = 100
    }
}
