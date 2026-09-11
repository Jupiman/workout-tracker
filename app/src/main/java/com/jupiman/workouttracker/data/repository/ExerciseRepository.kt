package com.jupiman.workouttracker.data.repository

import com.jupiman.workouttracker.data.local.dao.ExerciseDao
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity

class ExerciseRepository(
    private val exerciseDao: ExerciseDao,
) {
    val exercises = exerciseDao.observeActive()

    suspend fun getExercise(id: Long): ExerciseEntity? = exerciseDao.getById(id)
}

