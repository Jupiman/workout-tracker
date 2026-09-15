package com.jupiman.workouttracker.healthconnect

import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus

enum class HealthConnectAvailability {
    AVAILABLE,
    PROVIDER_UPDATE_REQUIRED,
    UNAVAILABLE,
}

enum class HealthConnectExerciseType {
    STRENGTH_TRAINING,
}

data class HealthConnectWorkoutRecord(
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val title: String,
    val notes: String,
    val clientRecordId: String,
    val clientRecordVersion: Long = 0,
    val exerciseType: HealthConnectExerciseType = HealthConnectExerciseType.STRENGTH_TRAINING,
)

fun WorkoutSessionEntity.toHealthConnectWorkoutRecord(): HealthConnectWorkoutRecord? {
    if (status != WorkoutSessionStatus.COMPLETED && status != WorkoutSessionStatus.PARTIAL) return null
    val endTime = completedAt ?: return null
    if (endTime <= startedAt) return null
    return HealthConnectWorkoutRecord(
        startTimeMillis = startedAt,
        endTimeMillis = endTime,
        title = workoutNameSnapshot,
        notes = if (status == WorkoutSessionStatus.PARTIAL) {
            "$programNameSnapshot · Partial workout"
        } else {
            programNameSnapshot
        },
        clientRecordId = "workout-companion-session-$id-$startedAt",
    )
}

data class HealthConnectSettingsState(
    val availability: HealthConnectAvailability,
    val hasWritePermission: Boolean,
)

sealed interface HealthConnectSyncResult {
    data class Completed(val recordCount: Int) : HealthConnectSyncResult
    data object Disabled : HealthConnectSyncResult
    data object PermissionRequired : HealthConnectSyncResult
    data object ProviderUpdateRequired : HealthConnectSyncResult
    data object Unavailable : HealthConnectSyncResult
    data object Failed : HealthConnectSyncResult
}
