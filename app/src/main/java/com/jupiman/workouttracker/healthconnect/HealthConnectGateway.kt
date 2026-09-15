package com.jupiman.workouttracker.healthconnect

interface HealthConnectGateway {
    suspend fun availability(): HealthConnectAvailability
    suspend fun hasWriteExercisePermission(): Boolean
    suspend fun writeExerciseSessions(records: List<HealthConnectWorkoutRecord>)
}

interface FinalizedWorkoutSource {
    suspend fun finalizedWorkouts(): List<com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity>
    suspend fun finalizedWorkout(sessionId: Long): com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity?
}

fun interface FinalizedWorkoutSync {
    fun syncAfterFinalization(sessionId: Long)
}

object NoOpFinalizedWorkoutSync : FinalizedWorkoutSync {
    override fun syncAfterFinalization(sessionId: Long) = Unit
}
