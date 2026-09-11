package com.jupiman.workouttracker.data.repository

import com.jupiman.workouttracker.data.local.dao.ProgramDao
import com.jupiman.workouttracker.data.local.dao.WorkoutTemplateDao

class ProgramRepository(
    private val programDao: ProgramDao,
    private val workoutTemplateDao: WorkoutTemplateDao,
) {
    val activeProgram = programDao.observeActive()
    val programs = programDao.observeAllActive()

    fun workoutTemplates(programId: Long) = workoutTemplateDao.observeForProgram(programId)
}

