package com.jupiman.workouttracker.wear

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
}
