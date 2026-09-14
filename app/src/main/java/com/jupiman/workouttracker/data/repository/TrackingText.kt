package com.jupiman.workouttracker.data.repository

import com.jupiman.workouttracker.data.local.entity.*

fun trackingText(mode: TrackingMode, weight: Int?, reps: Int?, seconds: Int?): String = when (mode) {
    TrackingMode.WEIGHT_REPS -> "${weight?.let(::formatCentiKg) ?: "-"} kg × ${reps ?: "-"}"
    TrackingMode.REPS -> "${reps ?: "-"} reps"
    TrackingMode.DURATION -> "${seconds ?: "-"} sec"
}

fun SessionExerciseEntity.targetText() = trackingText(trackingModeSnapshot, prescribedWeightCentiKgSnapshot, targetRepsSnapshot, targetDurationSecondsSnapshot)

fun SessionSetEntity.trackingText(mode: TrackingMode, actual: Boolean = true): String = trackingText(
    mode,
    if (actual) actualWeightCentiKg ?: prescribedWeightCentiKg else prescribedWeightCentiKg,
    if (actual) actualReps ?: prescribedReps else prescribedReps,
    if (actual) actualDurationSeconds ?: prescribedDurationSeconds else prescribedDurationSeconds,
)
