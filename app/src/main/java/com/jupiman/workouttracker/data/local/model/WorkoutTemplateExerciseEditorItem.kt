package com.jupiman.workouttracker.data.local.model

data class WorkoutTemplateExerciseEditorItem(
    val id: Long,
    val workoutTemplateId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val sortOrder: Int,
    val plannedWorkingSets: Int,
    val repMin: Int,
    val repMax: Int,
    val incrementCentiKg: Int,
    val restSeconds: Int,
    val setupNote: String,
    val supersetGroupId: Long?,
    val currentWeightCentiKg: Int,
    val currentTargetReps: Int,
    val trackingMode: com.jupiman.workouttracker.data.local.entity.TrackingMode = com.jupiman.workouttracker.data.local.entity.TrackingMode.WEIGHT_REPS,
    val targetDurationSeconds: Int? = null,
    val durationIncrementSeconds: Int = 0,
    val warmupRoundingCentiKg: Int = 500,
    val syncId: String,
)
