package com.jupiman.workouttracker.data.repository

import com.jupiman.workouttracker.data.local.entity.TrackingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackingConfigTest {
    private val config = TemplateExerciseConfig(3, 8, 12, 250, 180, 5000, 8)

    @Test
    fun durationUsesSecondsAndIgnoresHiddenWeightAndRepFields() {
        val result = config.copy(trackingMode = TrackingMode.DURATION, targetDurationSeconds = 60,
            durationIncrementSeconds = 5, repMin = -1, repMax = -1, currentTargetReps = -1,
            currentWeightCentiKg = -1, incrementCentiKg = -1).validatedForCreate()
        assertEquals(60, result.targetDurationSeconds)
        assertEquals(5, result.durationIncrementSeconds)
        assertEquals(0, result.currentWeightCentiKg)
        assertEquals("60 sec", trackingText(result.trackingMode, 5000, 8, result.targetDurationSeconds))
    }

    @Test
    fun repsIgnoresHiddenWeightAndRetainsRepRange() {
        val result = config.copy(trackingMode = TrackingMode.REPS, currentWeightCentiKg = -1,
            incrementCentiKg = -1, targetDurationSeconds = 60).validatedForCreate()
        assertEquals(0, result.currentWeightCentiKg)
        assertEquals(8, result.currentTargetReps)
        assertNull(result.targetDurationSeconds)
        assertEquals("8 reps", trackingText(result.trackingMode, 5000, 8, 60))
    }

    @Test(expected = IllegalArgumentException::class)
    fun durationRejectsNegativeIncrement() {
        config.copy(trackingMode = TrackingMode.DURATION, targetDurationSeconds = 60,
            durationIncrementSeconds = -1).validatedForCreate()
    }

    @Test(expected = IllegalArgumentException::class)
    fun durationRejectsMissingTarget() {
        config.copy(trackingMode = TrackingMode.DURATION).validatedForCreate()
    }
}
