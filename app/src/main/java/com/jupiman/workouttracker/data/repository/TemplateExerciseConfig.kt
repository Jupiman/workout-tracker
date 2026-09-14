package com.jupiman.workouttracker.data.repository

import com.jupiman.workouttracker.data.local.entity.TrackingMode

data class TemplateExerciseConfig(
    val plannedWorkingSets: Int,
    val repMin: Int,
    val repMax: Int,
    val incrementCentiKg: Int,
    val restSeconds: Int,
    val currentWeightCentiKg: Int,
    val currentTargetReps: Int,
    val trackingMode: TrackingMode = TrackingMode.WEIGHT_REPS,
    val targetDurationSeconds: Int? = null,
    val durationIncrementSeconds: Int = 0,
)

fun TemplateExerciseConfig.validatedForCreate(): TemplateExerciseConfig {
    if (trackingMode != TrackingMode.WEIGHT_REPS) return validatedNonWeight(false)
    validateTemplateExerciseConfig(
        plannedWorkingSets = plannedWorkingSets,
        repMin = repMin,
        repMax = repMax,
        incrementCentiKg = incrementCentiKg,
        restSeconds = restSeconds,
    )
    require(currentWeightCentiKg >= 0) { "Current weight cannot be negative." }
    require(currentTargetReps in repMin..repMax) { "Current target reps must be inside the rep range." }
    return copy(targetDurationSeconds = null, durationIncrementSeconds = 0)
}

fun TemplateExerciseConfig.validatedForUpdate(): TemplateExerciseConfig {
    if (trackingMode != TrackingMode.WEIGHT_REPS) return validatedNonWeight(true)
    validateTemplateExerciseConfig(
        plannedWorkingSets = plannedWorkingSets,
        repMin = repMin,
        repMax = repMax,
        incrementCentiKg = incrementCentiKg,
        restSeconds = restSeconds,
    )
    require(currentWeightCentiKg >= 0) { "Current weight cannot be negative." }
    return copy(currentTargetReps = currentTargetReps.coerceIn(repMin, repMax), targetDurationSeconds = null, durationIncrementSeconds = 0)
}

private fun TemplateExerciseConfig.validatedNonWeight(clamp: Boolean): TemplateExerciseConfig {
    require(plannedWorkingSets >= 1) { "Working sets must be at least 1." }
    require(restSeconds >= 0) { "Rest time cannot be negative." }
    if (trackingMode == TrackingMode.DURATION) {
        require(durationIncrementSeconds >= 0) { "Duration increment cannot be negative." }
        require(targetDurationSeconds != null && targetDurationSeconds >= 1) { "Duration must be at least 1 second." }
        return copy(currentWeightCentiKg = 0, incrementCentiKg = 250, repMin = 1, repMax = 1, currentTargetReps = 1)
    }
    require(repMin >= 1 && repMax >= repMin) { "Enter a valid rep range." }
    require(clamp || currentTargetReps in repMin..repMax) { "Target reps must be inside the rep range." }
    return copy(currentWeightCentiKg = 0, incrementCentiKg = 250, targetDurationSeconds = null, durationIncrementSeconds = 0,
        currentTargetReps = currentTargetReps.coerceIn(repMin, repMax))
}

private fun validateTemplateExerciseConfig(
    plannedWorkingSets: Int,
    repMin: Int,
    repMax: Int,
    incrementCentiKg: Int,
    restSeconds: Int,
) {
    require(plannedWorkingSets >= 1) { "Working sets must be at least 1." }
    require(repMin >= 1) { "Minimum reps must be at least 1." }
    require(repMax >= repMin) { "Maximum reps must be greater than or equal to minimum reps." }
    require(incrementCentiKg > 0) { "Increment must be greater than 0 kg." }
    require(restSeconds >= 0) { "Rest time cannot be negative." }
}
