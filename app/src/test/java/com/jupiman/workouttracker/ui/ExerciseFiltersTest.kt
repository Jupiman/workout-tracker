package com.jupiman.workouttracker.ui

import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseFiltersTest {
    @Test
    fun exerciseSearchUsesCaseInsensitiveSubstringMatching() {
        val exercises = listOf(
            exercise("Bench Press"),
            exercise("Incline Bench Press"),
            exercise("Lat Pulldown"),
            exercise("Dumbbell Lateral Raise"),
            exercise("Cable Lateral Raise"),
            exercise("Custom Sled Push"),
        )

        assertEquals(
            listOf("Bench Press", "Incline Bench Press"),
            exercises.filterByExerciseSearchQuery("bench").map { it.name },
        )
        assertEquals(
            listOf("Lat Pulldown", "Dumbbell Lateral Raise", "Cable Lateral Raise"),
            exercises.filterByExerciseSearchQuery("LAT").map { it.name },
        )
        assertEquals(
            listOf("Custom Sled Push"),
            exercises.filterByExerciseSearchQuery("sled").map { it.name },
        )
        assertEquals(
            emptyList<String>(),
            exercises.filterByExerciseSearchQuery("xyznonexistent").map { it.name },
        )
    }

    private fun exercise(name: String) = ExerciseEntity(
        name = name,
        createdAt = 1_000L,
    )
}

