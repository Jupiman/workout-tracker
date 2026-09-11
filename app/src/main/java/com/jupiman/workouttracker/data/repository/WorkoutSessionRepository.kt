package com.jupiman.workouttracker.data.repository

import com.jupiman.workouttracker.data.local.dao.SessionExerciseDao
import com.jupiman.workouttracker.data.local.dao.SessionSetDao
import com.jupiman.workouttracker.data.local.dao.WorkoutSessionDao

class WorkoutSessionRepository(
    private val workoutSessionDao: WorkoutSessionDao,
    private val sessionExerciseDao: SessionExerciseDao,
    private val sessionSetDao: SessionSetDao,
) {
    val activeSession = workoutSessionDao.observeActive()
    val history = workoutSessionDao.observeHistory()

    fun sessionExercises(sessionId: Long) = sessionExerciseDao.observeForSession(sessionId)

    fun sessionSets(sessionExerciseId: Long) = sessionSetDao.observeForSessionExercise(sessionExerciseId)
}

