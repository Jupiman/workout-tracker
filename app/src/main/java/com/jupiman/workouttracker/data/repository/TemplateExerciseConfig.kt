package com.jupiman.workouttracker.data.repository

data class TemplateExerciseConfig(
    val plannedWorkingSets: Int,
    val repMin: Int,
    val repMax: Int,
    val incrementCentiKg: Int,
    val restSeconds: Int,
    val currentWeightCentiKg: Int,
    val currentTargetReps: Int,
)

fun TemplateExerciseConfig.validatedForCreate(): TemplateExerciseConfig {
    validateTemplateExerciseConfig(
        plannedWorkingSets = plannedWorkingSets,
        repMin = repMin,
        repMax = repMax,
        incrementCentiKg = incrementCentiKg,
        restSeconds = restSeconds,
    )
    require(currentWeightCentiKg >= 0) { "Current weight cannot be negative." }
    require(currentTargetReps in repMin..repMax) { "Current target reps must be inside the rep range." }
    return this
}

fun TemplateExerciseConfig.validatedForUpdate(): TemplateExerciseConfig {
    validateTemplateExerciseConfig(
        plannedWorkingSets = plannedWorkingSets,
        repMin = repMin,
        repMax = repMax,
        incrementCentiKg = incrementCentiKg,
        restSeconds = restSeconds,
    )
    require(currentWeightCentiKg >= 0) { "Current weight cannot be negative." }
    return copy(currentTargetReps = currentTargetReps.coerceIn(repMin, repMax))
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
