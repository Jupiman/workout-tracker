package com.jupiman.workouttracker.data.repository

import com.jupiman.workouttracker.preferences.WeightUnit
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

    @Test
    fun poundsArePresentationAndInputOverCanonicalCentiKg() {
        val canonicalCentiKg = 10_000

        assertEquals("100 kg", formatWeight(canonicalCentiKg, WeightUnit.KG))
        assertEquals("220.46 lb", formatWeight(canonicalCentiKg, WeightUnit.LB))
        assertEquals(canonicalCentiKg, parseWeight("220.46", WeightUnit.LB))
    }

    @Test
    fun formattedPoundsRoundTripToTheSameCanonicalWeight() {
        listOf(0, 125, 7_255, 10_000, 25_000).forEach { canonicalCentiKg ->
            val displayed = formatWeightValue(canonicalCentiKg, WeightUnit.LB)
            assertEquals(canonicalCentiKg, parseWeight(displayed, WeightUnit.LB))
        }
    }
}
