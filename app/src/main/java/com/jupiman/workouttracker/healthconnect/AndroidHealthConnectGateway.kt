package com.jupiman.workouttracker.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant

class AndroidHealthConnectGateway(context: Context) : HealthConnectGateway {
    private val appContext = context.applicationContext

    override suspend fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(appContext, PROVIDER_PACKAGE_NAME)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED
            else -> HealthConnectAvailability.UNAVAILABLE
        }

    override suspend fun hasWriteExercisePermission(): Boolean {
        if (availability() != HealthConnectAvailability.AVAILABLE) return false
        return WRITE_EXERCISE_PERMISSION in client().permissionController.getGrantedPermissions()
    }

    override suspend fun writeExerciseSessions(records: List<HealthConnectWorkoutRecord>) {
        if (records.isEmpty()) return
        client().insertRecords(records.map { record -> record.toExerciseSessionRecord() })
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(appContext, PROVIDER_PACKAGE_NAME)

    private fun HealthConnectWorkoutRecord.toExerciseSessionRecord() = ExerciseSessionRecord(
        startTime = Instant.ofEpochMilli(startTimeMillis),
        startZoneOffset = null,
        endTime = Instant.ofEpochMilli(endTimeMillis),
        endZoneOffset = null,
        exerciseType = when (exerciseType) {
            HealthConnectExerciseType.STRENGTH_TRAINING -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        },
        title = title,
        notes = notes,
        metadata = Metadata.manualEntry(
            clientRecordId = clientRecordId,
            clientRecordVersion = clientRecordVersion,
        ),
    )

    companion object {
        const val PROVIDER_PACKAGE_NAME = "com.google.android.apps.healthdata"
        val WRITE_EXERCISE_PERMISSION: String = HealthPermission.getWritePermission(ExerciseSessionRecord::class)
    }
}
