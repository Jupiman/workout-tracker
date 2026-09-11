package com.jupiman.workouttracker.domain.progression

import com.jupiman.workouttracker.data.local.entity.SessionSetStatus

data class ProgressionConfig(
    val currentWeightCentiKg: Int,
    val currentTargetReps: Int,
    val repMin: Int,
    val repMax: Int,
    val incrementCentiKg: Int,
)

data class ProgressionSet(
    val countsForProgression: Boolean,
    val prescribedWeightCentiKg: Int?,
    val prescribedReps: Int?,
    val actualWeightCentiKg: Int?,
    val actualReps: Int?,
    val status: SessionSetStatus,
)

data class ProgressionResult(
    val progressed: Boolean,
    val nextWeightCentiKg: Int,
    val nextTargetReps: Int,
)

object ProgressionEngine {
    fun evaluate(
        config: ProgressionConfig,
        sets: List<ProgressionSet>,
    ): ProgressionResult {
        val relevantSets = sets.filter { it.countsForProgression }
        val successful = relevantSets.isNotEmpty() &&
            relevantSets.all { set ->
                set.status == SessionSetStatus.COMPLETED &&
                    set.actualWeightCentiKg != null &&
                    set.prescribedWeightCentiKg != null &&
                    set.actualWeightCentiKg == set.prescribedWeightCentiKg &&
                    set.actualReps != null &&
                    set.prescribedReps != null &&
                    set.actualReps >= set.prescribedReps
            }

        if (!successful) {
            return ProgressionResult(
                progressed = false,
                nextWeightCentiKg = config.currentWeightCentiKg,
                nextTargetReps = config.currentTargetReps,
            )
        }

        return if (config.currentTargetReps < config.repMax) {
            ProgressionResult(
                progressed = true,
                nextWeightCentiKg = config.currentWeightCentiKg,
                nextTargetReps = config.currentTargetReps + 1,
            )
        } else {
            ProgressionResult(
                progressed = true,
                nextWeightCentiKg = config.currentWeightCentiKg + config.incrementCentiKg,
                nextTargetReps = config.repMin,
            )
        }
    }
}
