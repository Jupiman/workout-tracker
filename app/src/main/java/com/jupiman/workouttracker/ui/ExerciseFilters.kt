package com.jupiman.workouttracker.ui

import com.jupiman.workouttracker.data.local.entity.ExerciseEntity

internal fun List<ExerciseEntity>.filterByExerciseSearchQuery(query: String): List<ExerciseEntity> {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) return this
    return filter { exercise ->
        exercise.name.contains(trimmedQuery, ignoreCase = true)
    }
}

