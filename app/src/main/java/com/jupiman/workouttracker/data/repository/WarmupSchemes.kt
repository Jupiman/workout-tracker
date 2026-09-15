package com.jupiman.workouttracker.data.repository

import com.jupiman.workouttracker.data.local.entity.WarmupLoadType
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateSetTargetEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateWarmupSetEntity
import kotlin.math.roundToInt

data class WarmupSetConfiguration(
    val reps: Int,
    val loadType: WarmupLoadType,
    val percentOfWorkingWeight: Int? = null,
    val fixedWeightCentiKg: Int? = null,
) {
    fun validated(): WarmupSetConfiguration {
        require(reps >= 1) { "Warm-up reps must be at least 1." }
        when (loadType) {
            WarmupLoadType.PERCENTAGE -> {
                require(percentOfWorkingWeight != null && percentOfWorkingWeight in 1..100) {
                    "Warm-up percentage must be between 1 and 100."
                }
                require(fixedWeightCentiKg == null) { "Percentage warm-ups cannot contain a fixed weight." }
            }
            WarmupLoadType.FIXED -> {
                require(fixedWeightCentiKg != null && fixedWeightCentiKg >= 0) {
                    "Fixed warm-up weight cannot be negative."
                }
                require(percentOfWorkingWeight == null) { "Fixed warm-ups cannot contain a percentage." }
            }
        }
        return this
    }
}

enum class WarmupPreset(
    val displayName: String,
    val sets: List<WarmupSetConfiguration>,
) {
    NONE("None", emptyList()),
    MINIMAL(
        "Minimal",
        listOf(percentageWarmup(percent = 60, reps = 4), percentageWarmup(percent = 80, reps = 2)),
    ),
    STANDARD(
        "Standard",
        listOf(
            percentageWarmup(percent = 50, reps = 5),
            percentageWarmup(percent = 70, reps = 3),
            percentageWarmup(percent = 85, reps = 1),
        ),
    ),
    HEAVY(
        "Heavy",
        listOf(
            percentageWarmup(percent = 40, reps = 5),
            percentageWarmup(percent = 60, reps = 3),
            percentageWarmup(percent = 80, reps = 2),
            percentageWarmup(percent = 90, reps = 1),
        ),
    ),
    CUSTOM("Custom", emptyList()),
}

fun identifyWarmupPreset(sets: List<WorkoutTemplateWarmupSetEntity>): WarmupPreset {
    val configurations = sets.sortedBy { it.sortOrder }.map {
        WarmupSetConfiguration(
            reps = it.reps,
            loadType = it.loadType,
            percentOfWorkingWeight = it.percentOfWorkingWeight,
            fixedWeightCentiKg = it.fixedWeightCentiKg,
        )
    }
    return WarmupPreset.entries.firstOrNull { preset ->
        preset != WarmupPreset.CUSTOM && preset.sets == configurations
    } ?: WarmupPreset.CUSTOM
}

fun warmupReferenceWeightCentiKg(
    defaultWorkingWeightCentiKg: Int,
    plannedWorkingSets: Int,
    setTargets: List<WorkoutTemplateSetTargetEntity>,
): Int {
    val targetsByOrder = setTargets.associateBy { it.setOrder }
    return (0 until plannedWorkingSets).mapNotNull { setOrder ->
        val target = targetsByOrder[setOrder]
        if (target?.countsForProgression == false) null
        else target?.prescribedWeightCentiKg ?: defaultWorkingWeightCentiKg
    }.maxOrNull()?.coerceAtLeast(0) ?: 0
}

fun warmupPrescribedWeightCentiKg(
    warmup: WarmupSetConfiguration,
    referenceWeightCentiKg: Int,
    roundingCentiKg: Int,
): Int {
    warmup.validated()
    return when (warmup.loadType) {
        WarmupLoadType.FIXED -> warmup.fixedWeightCentiKg!!
        WarmupLoadType.PERCENTAGE -> {
            if (referenceWeightCentiKg <= 0) return 0
            val raw = referenceWeightCentiKg * warmup.percentOfWorkingWeight!! / 100.0
            val rounded = if (roundingCentiKg > 0) {
                (raw / roundingCentiKg).roundToInt() * roundingCentiKg
            } else {
                raw.roundToInt()
            }
            rounded.coerceIn(0, referenceWeightCentiKg)
        }
    }
}

private fun percentageWarmup(percent: Int, reps: Int) = WarmupSetConfiguration(
    reps = reps,
    loadType = WarmupLoadType.PERCENTAGE,
    percentOfWorkingWeight = percent,
)
