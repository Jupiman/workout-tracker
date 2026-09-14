package com.jupiman.workouttracker.data.repository

data class WorkoutCompletionSummary(
    val sessionId: Long,
    val workoutName: String,
    val durationSeconds: Long,
    val partial: Boolean,
    val completedWorkingSets: Int,
    val totalWorkingSets: Int,
    val skippedSets: Int,
    val exercises: List<ExerciseCompletionSummary>,
)

data class ExerciseCompletionSummary(
    val sessionExerciseId: Long,
    val name: String,
    val before: List<CompletionTarget>?,
    val after: List<CompletionTarget>?,
)

data class CompletionTarget(
    val weightCentiKg: Int, val reps: Int,
    val trackingMode: com.jupiman.workouttracker.data.local.entity.TrackingMode = com.jupiman.workouttracker.data.local.entity.TrackingMode.WEIGHT_REPS,
    val durationSeconds: Int? = null,
)
