package com.jupiman.workouttracker.wear

import com.jupiman.workouttracker.wearprotocol.WearSessionStatus
import com.jupiman.workouttracker.wearprotocol.WearTrackingMode
import com.jupiman.workouttracker.wearprotocol.WorkoutWearState
import com.jupiman.workouttracker.wearprotocol.WearWeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearWorkoutFormatterTest {
    @Test
    fun restCountdownIsReconstructedFromDeadline() {
        assertEquals("Rest 1:30", restText(restEndsAt = 90_000L, now = 0L))
        assertEquals("Rest 0:01", restText(restEndsAt = 90_000L, now = 89_500L))
        assertEquals("Rest -0:00", restText(restEndsAt = 90_000L, now = 90_000L))
        assertEquals("Rest -0:01", restText(restEndsAt = 90_000L, now = 90_001L))
        assertEquals("Rest -1:30", restText(restEndsAt = 90_000L, now = 180_000L))
    }

    @Test
    fun noDeadlineProducesNoRestText() {
        assertNull(restText(restEndsAt = null, now = 0L))
    }

    @Test
    fun centiKgFormatsCompactly() {
        assertEquals("70", formatCentiKg(7000))
        assertEquals("72.5", formatCentiKg(7250))
        assertEquals("72.55", formatCentiKg(7255))
    }

    @Test
    fun targetsUseTheSelectedTrackingMode() {
        val weighted = state().copy(weightCentiKg = 7250, targetReps = 10)
        assertEquals("72.5kg × 10", targetText(weighted))
        assertEquals("10 reps", targetText(weighted.copy(trackingMode = WearTrackingMode.REPS, weightCentiKg = null)))
        assertEquals("60 sec", targetText(weighted.copy(
            trackingMode = WearTrackingMode.DURATION,
            weightCentiKg = null,
            targetReps = null,
            targetDurationSeconds = 60,
        )))
    }

    @Test
    fun longWeightedTargetsUseAFontSizeThatFitsTheWatch() {
        val threeDigitWeight = targetText(state().copy(weightCentiKg = 10_000, targetReps = 99))
        val decimalWeight = targetText(state().copy(weightCentiKg = 10_025, targetReps = 99))

        assertEquals("100kg × 99", threeDigitWeight)
        assertEquals(20, targetFontSizeSp(threeDigitWeight))
        assertEquals("100.25kg × 99", decimalWeight)
        assertEquals(18, targetFontSizeSp(decimalWeight))
    }

    @Test
    fun poundsUseTheProtocolUnitWithoutChangingCanonicalWeight() {
        val pounds = state().copy(weightCentiKg = 10_000, targetReps = 8, weightUnit = WearWeightUnit.LB)

        assertEquals("220.46lb × 8", targetText(pounds))
        assertEquals(10_000, pounds.weightCentiKg)
    }

    @Test
    fun fiveSecondPreparationUsesTheAuthoritativeStartTimestamp() {
        val preparing = state().copy(durationStartsAt = 6_000L, durationEndsAt = 66_000L)

        assertEquals("5", durationTimerText(preparing, now = 1_000L))
        assertEquals("4", durationTimerText(preparing, now = 2_000L))
        assertEquals("1", durationTimerText(preparing, now = 5_000L))
        assertEquals("GO", durationTimerText(preparing, now = 6_000L))
    }

    private fun state() = WorkoutWearState(
        sessionId = 1,
        sessionStatus = WearSessionStatus.ACTIVE,
        currentSetId = 2,
        exerciseName = "Exercise",
        weightCentiKg = null,
        targetReps = null,
        setLabel = "Set 1/1",
        setNumber = 1,
        totalSets = 1,
        restEndsAt = null,
        supersetPosition = null,
        supersetSize = null,
        stateVersion = 1,
        updatedAt = 1,
    )
}
