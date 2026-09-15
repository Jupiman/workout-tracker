package com.jupiman.workouttracker.data.repository

import com.jupiman.workouttracker.data.local.entity.WarmupLoadType
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateSetTargetEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateWarmupSetEntity
import com.jupiman.workouttracker.preferences.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class WarmupSchemesTest {
    @Test
    fun presetsContainTheRequiredConcreteRows() {
        assertEquals(listOf(60 to 4, 80 to 2), WarmupPreset.MINIMAL.percentagePairs())
        assertEquals(listOf(50 to 5, 70 to 3, 85 to 1), WarmupPreset.STANDARD.percentagePairs())
        assertEquals(listOf(40 to 5, 60 to 3, 80 to 2, 90 to 1), WarmupPreset.HEAVY.percentagePairs())
        assertEquals(emptyList<WarmupSetConfiguration>(), WarmupPreset.NONE.sets)
    }

    @Test
    fun legacyDefaultRowsRemainConcreteAndAreRecognizedAsCustom() {
        val rows = listOf(30 to 10, 70 to 3, 75 to 3).mapIndexed { index, (percent, reps) ->
            WorkoutTemplateWarmupSetEntity(
                workoutTemplateExerciseId = 1,
                sortOrder = index,
                reps = reps,
                loadType = WarmupLoadType.PERCENTAGE,
                percentOfWorkingWeight = percent,
            )
        }

        assertEquals(WarmupPreset.CUSTOM, identifyWarmupPreset(rows))
        assertEquals(listOf(30, 70, 75), rows.map { it.percentOfWorkingWeight })
    }

    @Test
    fun percentageFixedAndMixedRowsValidate() {
        percentage(70, 3).validated()
        fixed(2_000, 8).validated()
        listOf(fixed(2_000, 8), percentage(50, 5), percentage(70, 3)).forEach { it.validated() }
    }

    @Test
    fun malformedRowsAreRejected() {
        assertInvalid { percentage(0, 3).validated() }
        assertInvalid { percentage(101, 3).validated() }
        assertInvalid { percentage(70, 0).validated() }
        assertInvalid {
            WarmupSetConfiguration(3, WarmupLoadType.PERCENTAGE, 70, 2_000).validated()
        }
        assertInvalid {
            WarmupSetConfiguration(3, WarmupLoadType.FIXED, 70, 2_000).validated()
        }
        assertInvalid { fixed(-1, 3).validated() }
    }

    @Test
    fun percentageUsesNearestIncrementAndNeverExceedsReference() {
        assertEquals(5_000, warmupPrescribedWeightCentiKg(percentage(70, 3), 7_300, 500))
        assertEquals(5_110, warmupPrescribedWeightCentiKg(percentage(70, 3), 7_300, 0))
        assertEquals(7_300, warmupPrescribedWeightCentiKg(percentage(100, 1), 7_300, 500))
    }

    @Test
    fun fixedWeightBypassesRounding() {
        assertEquals(2_125, warmupPrescribedWeightCentiKg(fixed(2_125, 8), 7_300, 500))
    }

    @Test
    fun referenceUsesHighestProgressionRelevantPrescribedWorkingSet() {
        val targets = listOf(
            target(0, 10_000),
            target(1, 9_000),
            target(2, 9_000),
        )
        assertEquals(10_000, warmupReferenceWeightCentiKg(8_000, 3, targets))
        assertEquals(8_000, warmupReferenceWeightCentiKg(8_000, 3, emptyList()))
        assertEquals(
            8_000,
            warmupReferenceWeightCentiKg(8_000, 3, listOf(target(0, 12_000, counts = false))),
        )
    }

    @Test
    fun fixedAndRoundingValuesRemainCanonicalAcrossKgAndLbDisplay() {
        val fixedCentiKg = parseWeight("20", WeightUnit.KG)
        val displayedLb = formatWeightValue(fixedCentiKg, WeightUnit.LB)
        assertEquals(fixedCentiKg, parseWeight(displayedLb, WeightUnit.LB))

        val fiveLbRoundingCentiKg = parseWeight("5", WeightUnit.LB)
        assertNotEquals(500, fiveLbRoundingCentiKg)
        assertEquals(fiveLbRoundingCentiKg, parseWeight(formatWeightValue(fiveLbRoundingCentiKg, WeightUnit.KG), WeightUnit.KG))
    }

    private fun WarmupPreset.percentagePairs() = sets.map { it.percentOfWorkingWeight!! to it.reps }

    private fun percentage(percent: Int, reps: Int) = WarmupSetConfiguration(
        reps = reps,
        loadType = WarmupLoadType.PERCENTAGE,
        percentOfWorkingWeight = percent,
    )

    private fun fixed(weightCentiKg: Int, reps: Int) = WarmupSetConfiguration(
        reps = reps,
        loadType = WarmupLoadType.FIXED,
        fixedWeightCentiKg = weightCentiKg,
    )

    private fun target(order: Int, weight: Int, counts: Boolean = true) = WorkoutTemplateSetTargetEntity(
        workoutTemplateExerciseId = 1,
        setOrder = order,
        prescribedWeightCentiKg = weight,
        prescribedReps = 8,
        countsForProgression = counts,
    )

    private fun assertInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid warm-up configuration.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
