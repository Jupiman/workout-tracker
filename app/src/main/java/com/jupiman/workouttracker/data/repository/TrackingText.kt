package com.jupiman.workouttracker.data.repository

import com.jupiman.workouttracker.data.local.entity.*
import com.jupiman.workouttracker.preferences.WeightUnit

fun trackingText(
    mode: TrackingMode,
    weight: Int?,
    reps: Int?,
    seconds: Int?,
    weightUnit: WeightUnit = WeightUnit.KG,
): String = when (mode) {
    TrackingMode.WEIGHT_REPS -> "${weight?.let { formatWeight(it, weightUnit) } ?: "- ${weightUnit.symbol}"} × ${reps ?: "-"}"
    TrackingMode.REPS -> "${reps ?: "-"} reps"
    TrackingMode.DURATION -> "${seconds ?: "-"} sec"
}

fun SessionExerciseEntity.targetText(weightUnit: WeightUnit = WeightUnit.KG) =
    trackingText(trackingModeSnapshot, prescribedWeightCentiKgSnapshot, targetRepsSnapshot, targetDurationSecondsSnapshot, weightUnit)

fun SessionSetEntity.trackingText(
    mode: TrackingMode,
    actual: Boolean = true,
    weightUnit: WeightUnit = WeightUnit.KG,
): String = trackingText(
    mode,
    if (actual) actualWeightCentiKg ?: prescribedWeightCentiKg else prescribedWeightCentiKg,
    if (actual) actualReps ?: prescribedReps else prescribedReps,
    if (actual) actualDurationSeconds ?: prescribedDurationSeconds else prescribedDurationSeconds,
    weightUnit,
)
