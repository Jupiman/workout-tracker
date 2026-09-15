package com.jupiman.workouttracker.domain.progression

import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.TrackingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressionEngineTest {
    @Test
    fun fixedRepWeightedSuccessIncrementsWeightAndKeepsRepsFixed() {
        val first = ProgressionEngine.evaluate(
            config = config(weight = 7000, targetReps = 8, repMin = 8, repMax = 8),
            sets = plannedSuccessSets(weight = 7000, targetReps = 8),
        )
        val second = ProgressionEngine.evaluate(
            config = config(
                weight = first.nextWeightCentiKg,
                targetReps = first.nextTargetReps,
                repMin = 8,
                repMax = 8,
            ),
            sets = plannedSuccessSets(weight = 7250, targetReps = 8),
        )

        assertTrue(first.progressed)
        assertEquals(7250, first.nextWeightCentiKg)
        assertEquals(8, first.nextTargetReps)
        assertTrue(second.progressed)
        assertEquals(7500, second.nextWeightCentiKg)
        assertEquals(8, second.nextTargetReps)
    }

    @Test
    fun fixedRepWeightedFailureOrSkipPreservesTarget() {
        val fixedConfig = config(weight = 7000, targetReps = 8, repMin = 8, repMax = 8)
        val failed = ProgressionEngine.evaluate(
            config = fixedConfig,
            sets = plannedSuccessSets(weight = 7000, targetReps = 8).mapIndexed { index, set ->
                if (index == 2) set.copy(actualReps = 7) else set
            },
        )
        val skipped = ProgressionEngine.evaluate(
            config = fixedConfig,
            sets = plannedSuccessSets(weight = 7000, targetReps = 8).mapIndexed { index, set ->
                if (index == 2) set.copy(status = SessionSetStatus.SKIPPED) else set
            },
        )

        listOf(failed, skipped).forEach { result ->
            assertFalse(result.progressed)
            assertEquals(7000, result.nextWeightCentiKg)
            assertEquals(8, result.nextTargetReps)
        }
    }

    @Test
    fun fixedRepRepsModeRemainsFixedAfterSuccess() {
        val result = ProgressionEngine.evaluate(
            config = ProgressionConfig(0, 10, 10, 10, 250, TrackingMode.REPS),
            sets = List(3) { ProgressionSet(true, null, 10, null, 10, SessionSetStatus.COMPLETED) },
        )

        assertFalse(result.progressed)
        assertEquals(0, result.nextWeightCentiKg)
        assertEquals(10, result.nextTargetReps)
    }

    @Test
    fun repsModeAdvancesThenHoldsAtRepMaxWithoutWeightIncrease() {
        val config = ProgressionConfig(0, 10, 8, 12, 250, TrackingMode.REPS)
        val sets = List(3) { ProgressionSet(true, null, 10, null, 10, SessionSetStatus.COMPLETED) }
        val next = ProgressionEngine.evaluate(config, sets)
        assertEquals(11, next.nextTargetReps)
        assertEquals(0, next.nextWeightCentiKg)
        val max = ProgressionEngine.evaluate(config.copy(currentTargetReps = 12), sets.map { it.copy(prescribedReps = 12, actualReps = 12) })
        assertEquals(false, max.progressed)
        assertEquals(0, max.nextWeightCentiKg)
        assertEquals(12, max.nextTargetReps)
    }

    @Test
    fun durationModeDoesNotUseRepsProgression() {
        val config = ProgressionConfig(0, 1, 1, 1, 250, TrackingMode.DURATION)
        val sets = List(2) { ProgressionSet(true, null, null, null, null, SessionSetStatus.COMPLETED) }
        val result = ProgressionEngine.evaluate(config, sets)
        assertEquals(false, result.progressed)
    }
    @Test
    fun eightRepsSuccessProgressesToNineReps() {
        val result = evaluateSuccess(targetReps = 8, repMax = 12)

        assertTrue(result.progressed)
        assertEquals(7000, result.nextWeightCentiKg)
        assertEquals(9, result.nextTargetReps)
    }

    @Test
    fun nineRepsSuccessProgressesToTenReps() {
        val result = evaluateSuccess(targetReps = 9, repMax = 12)

        assertTrue(result.progressed)
        assertEquals(7000, result.nextWeightCentiKg)
        assertEquals(10, result.nextTargetReps)
    }

    @Test
    fun repMaxSuccessIncrementsWeightAndResetsToRepMin() {
        val result = evaluateSuccess(targetReps = 12, repMax = 12)

        assertTrue(result.progressed)
        assertEquals(7250, result.nextWeightCentiKg)
        assertEquals(8, result.nextTargetReps)
    }

    @Test
    fun oneFailedSetDoesNotProgress() {
        val result = ProgressionEngine.evaluate(
            config = config(targetReps = 10),
            sets = listOf(
                completedSet(actualReps = 10),
                completedSet(actualReps = 10),
                completedSet(actualReps = 9),
            ),
        )

        assertFalse(result.progressed)
        assertEquals(7000, result.nextWeightCentiKg)
        assertEquals(10, result.nextTargetReps)
    }

    @Test
    fun skippedSetDoesNotProgress() {
        val result = ProgressionEngine.evaluate(
            config = config(targetReps = 10),
            sets = listOf(
                completedSet(actualReps = 10),
                completedSet(actualReps = 10),
                completedSet(actualReps = 10).copy(status = SessionSetStatus.SKIPPED),
            ),
        )

        assertFalse(result.progressed)
        assertEquals(10, result.nextTargetReps)
    }

    @Test
    fun additionalNormalSetIsIgnored() {
        val result = ProgressionEngine.evaluate(
            config = config(targetReps = 10),
            sets = plannedSuccessSets(targetReps = 10) +
                completedSet(actualReps = 1, countsForProgression = false),
        )

        assertTrue(result.progressed)
        assertEquals(11, result.nextTargetReps)
    }

    @Test
    fun amrapSetIsIgnored() {
        val result = ProgressionEngine.evaluate(
            config = config(targetReps = 10),
            sets = plannedSuccessSets(targetReps = 10) +
                completedSet(actualReps = 30, countsForProgression = false),
        )

        assertTrue(result.progressed)
        assertEquals(11, result.nextTargetReps)
    }

    @Test
    fun dropSetIsIgnored() {
        val result = ProgressionEngine.evaluate(
            config = config(targetReps = 10),
            sets = plannedSuccessSets(targetReps = 10) +
                completedSet(actualWeight = 5000, actualReps = 20, countsForProgression = false),
        )

        assertTrue(result.progressed)
        assertEquals(11, result.nextTargetReps)
    }

    @Test
    fun sameExerciseInTwoTemplatesProgressesIndependently() {
        val dayA = ProgressionEngine.evaluate(
            config = config(weight = 7000, targetReps = 10, repMin = 8, repMax = 12),
            sets = plannedSuccessSets(weight = 7000, targetReps = 10),
        )
        val dayC = ProgressionEngine.evaluate(
            config = config(weight = 5500, targetReps = 12, repMin = 12, repMax = 15),
            sets = listOf(
                completedSet(weight = 5500, actualWeight = 5500, prescribedReps = 12, actualReps = 12),
                completedSet(weight = 5500, actualWeight = 5500, prescribedReps = 12, actualReps = 11),
                completedSet(weight = 5500, actualWeight = 5500, prescribedReps = 12, actualReps = 12),
            ),
        )

        assertEquals(11, dayA.nextTargetReps)
        assertEquals(7000, dayA.nextWeightCentiKg)
        assertFalse(dayC.progressed)
        assertEquals(12, dayC.nextTargetReps)
        assertEquals(5500, dayC.nextWeightCentiKg)
    }

    @Test
    fun twoInstancesOfSameExerciseInsideOneTemplateProgressIndependently() {
        val heavyInstance = ProgressionEngine.evaluate(
            config = config(weight = 7000, targetReps = 8, repMin = 8, repMax = 10),
            sets = plannedSuccessSets(weight = 7000, targetReps = 8),
        )
        val backOffInstance = ProgressionEngine.evaluate(
            config = config(weight = 5500, targetReps = 12, repMin = 12, repMax = 15),
            sets = plannedSuccessSets(weight = 5500, targetReps = 12),
        )

        assertEquals(9, heavyInstance.nextTargetReps)
        assertEquals(7000, heavyInstance.nextWeightCentiKg)
        assertEquals(13, backOffInstance.nextTargetReps)
        assertEquals(5500, backOffInstance.nextWeightCentiKg)
    }

    @Test
    fun partialWorkoutUpdatesOnlyFullyCompletedExercises() {
        val completedExercise = ProgressionEngine.evaluate(
            config = config(targetReps = 10),
            sets = plannedSuccessSets(targetReps = 10),
        )
        val incompleteExercise = ProgressionEngine.evaluate(
            config = config(weight = 5000, targetReps = 10),
            sets = listOf(
                completedSet(weight = 5000, actualWeight = 5000, prescribedReps = 10, actualReps = 10),
                completedSet(weight = 5000, actualWeight = 5000, prescribedReps = 10, actualReps = 10),
                completedSet(weight = 5000, actualWeight = 5000, prescribedReps = 10, actualReps = null)
                    .copy(status = SessionSetStatus.PENDING),
            ),
        )

        assertTrue(completedExercise.progressed)
        assertEquals(11, completedExercise.nextTargetReps)
        assertFalse(incompleteExercise.progressed)
        assertEquals(10, incompleteExercise.nextTargetReps)
    }

    @Test
    fun moreRepsThanRequiredProgressesOnlyOneStep() {
        val result = ProgressionEngine.evaluate(
            config = config(targetReps = 10),
            sets = List(3) { completedSet(prescribedReps = 10, actualReps = 15) },
        )

        assertTrue(result.progressed)
        assertEquals(11, result.nextTargetReps)
    }

    private fun evaluateSuccess(targetReps: Int, repMax: Int): ProgressionResult =
        ProgressionEngine.evaluate(
            config = config(targetReps = targetReps, repMax = repMax),
            sets = plannedSuccessSets(targetReps = targetReps),
        )

    private fun config(
        weight: Int = 7000,
        targetReps: Int,
        repMin: Int = 8,
        repMax: Int = 12,
        increment: Int = 250,
    ) = ProgressionConfig(
        currentWeightCentiKg = weight,
        currentTargetReps = targetReps,
        repMin = repMin,
        repMax = repMax,
        incrementCentiKg = increment,
    )

    private fun plannedSuccessSets(
        weight: Int = 7000,
        targetReps: Int,
    ) = List(3) {
        completedSet(
            weight = weight,
            actualWeight = weight,
            prescribedReps = targetReps,
            actualReps = targetReps,
        )
    }

    private fun completedSet(
        weight: Int = 7000,
        actualWeight: Int = weight,
        prescribedReps: Int = 10,
        actualReps: Int? = prescribedReps,
        countsForProgression: Boolean = true,
    ) = ProgressionSet(
        countsForProgression = countsForProgression,
        prescribedWeightCentiKg = weight,
        prescribedReps = prescribedReps,
        actualWeightCentiKg = actualWeight,
        actualReps = actualReps,
        status = SessionSetStatus.COMPLETED,
    )
}
