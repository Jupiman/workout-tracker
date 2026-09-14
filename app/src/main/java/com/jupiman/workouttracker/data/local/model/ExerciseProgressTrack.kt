package com.jupiman.workouttracker.data.local.model

import com.jupiman.workouttracker.data.local.entity.TrackingMode

data class ExerciseProgressTrack(
    val workoutTemplateExerciseId: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val programId: Long,
    val programName: String,
    val workoutTemplateId: Long,
    val workoutName: String,
    val plannedWorkingSets: Int,
    val repMin: Int,
    val repMax: Int,
    val trackingMode: TrackingMode,
    val currentWeightCentiKg: Int,
    val currentTargetReps: Int,
    val targetDurationSeconds: Int?,
)
