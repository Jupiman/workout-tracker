package com.jupiman.workouttracker.healthconnect

interface HealthConnectGateway {
    suspend fun availability(): HealthConnectAvailability
    suspend fun hasWriteExercisePermission(): Boolean
    suspend fun writeExerciseSessions(records: List<HealthConnectWorkoutRecord>)
}

fun interface FinalizedWorkoutSource {
    suspend fun finalizedWorkouts(): List<com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity>
}

fun interface FinalizedWorkoutSync {
    suspend fun syncAfterFinalization()
}

object NoOpFinalizedWorkoutSync : FinalizedWorkoutSync {
    override suspend fun syncAfterFinalization() = Unit
}
