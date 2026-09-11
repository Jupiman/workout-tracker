package com.jupiman.workouttracker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TemplateExerciseConfigTest {
    @Test
    fun createConfigRequiresTargetRepsInsideRange() {
        assertThrows(IllegalArgumentException::class.java) {
            TemplateExerciseConfig(
                plannedWorkingSets = 3,
                repMin = 8,
                repMax = 12,
                incrementCentiKg = 250,
                restSeconds = 180,
                currentWeightCentiKg = 7000,
                currentTargetReps = 13,
            ).validatedForCreate()
        }
    }

    @Test
    fun updateConfigClampsTargetRepsIntoRange() {
        val config = TemplateExerciseConfig(
            plannedWorkingSets = 3,
            repMin = 6,
            repMax = 10,
            incrementCentiKg = 250,
            restSeconds = 180,
            currentWeightCentiKg = 7000,
            currentTargetReps = 12,
        ).validatedForUpdate()

        assertEquals(10, config.currentTargetReps)
    }

    @Test
    fun rejectsInvalidRepRange() {
        assertThrows(IllegalArgumentException::class.java) {
            TemplateExerciseConfig(
                plannedWorkingSets = 3,
                repMin = 12,
                repMax = 8,
                incrementCentiKg = 250,
                restSeconds = 180,
                currentWeightCentiKg = 7000,
                currentTargetReps = 10,
            ).validatedForCreate()
        }
    }
}
