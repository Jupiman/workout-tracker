package com.jupiman.workouttracker.data.repository

import com.jupiman.workouttracker.data.local.dao.ExerciseDao
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity

class ExerciseRepository(
    private val exerciseDao: ExerciseDao,
) {
    val exercises = exerciseDao.observeActive()

    suspend fun getExercise(id: Long): ExerciseEntity? = exerciseDao.getById(id)

    suspend fun createExercise(name: String): Long {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Exercise name cannot be empty." }

        val existing = exerciseDao.getByName(trimmedName)
        if (existing != null) {
            if (existing.archived) {
                exerciseDao.update(existing.copy(archived = false))
            }
            return existing.id
        }

        return exerciseDao.insert(
            ExerciseEntity(
                name = trimmedName,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun renameExercise(id: Long, name: String) {
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Exercise name cannot be empty." }

        val exercise = exerciseDao.getById(id) ?: error("Exercise not found.")
        val existing = exerciseDao.getByName(trimmedName)
        require(existing == null || existing.id == id) { "An exercise with that name already exists." }

        exerciseDao.update(exercise.copy(name = trimmedName))
    }

    suspend fun archiveExercise(id: Long) {
        exerciseDao.archive(id)
    }
}
