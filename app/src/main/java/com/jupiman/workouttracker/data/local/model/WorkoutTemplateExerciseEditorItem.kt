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
    val supersetGroupId: Long?,
    val currentWeightCentiKg: Int,
    val currentTargetReps: Int,
)

