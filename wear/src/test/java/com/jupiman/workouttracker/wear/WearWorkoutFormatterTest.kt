package com.jupiman.workouttracker.wear

import com.jupiman.workouttracker.wearprotocol.WearSessionStatus
import com.jupiman.workouttracker.wearprotocol.WearTrackingMode
import com.jupiman.workouttracker.wearprotocol.WorkoutWearState
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
