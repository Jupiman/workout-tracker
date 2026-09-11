package com.jupiman.workouttracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WeightTextTest {
    @Test
    fun parsesDecimalKilogramsToCentiKg() {
        assertEquals(7000, parseCentiKg("70"))
        assertEquals(7250, parseCentiKg("72.5"))
        assertEquals(125, parseCentiKg("1,25"))
    }

    @Test
    fun formatsCentiKgWithoutUnnecessaryDecimals() {
        assertEquals("70", formatCentiKg(7000))
        assertEquals("72.5", formatCentiKg(7250))
        assertEquals("1.25", formatCentiKg(125))
    }

    @Test
    fun rejectsNegativeWeight() {
        assertThrows(IllegalArgumentException::class.java) {
            parseCentiKg("-1")
        }
    }
}

