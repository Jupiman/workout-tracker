package com.jupiman.workouttracker.di

import android.content.Context
import androidx.room.Room
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import com.jupiman.workouttracker.data.repository.ExerciseRepository
import com.jupiman.workouttracker.data.repository.ProgramRepository
import com.jupiman.workouttracker.data.repository.WorkoutSessionRepository

class AppContainer(context: Context) {
    val database: WorkoutTrackerDatabase = Room.databaseBuilder(
        context.applicationContext,
        WorkoutTrackerDatabase::class.java,
        "workout_tracker.db",
    ).build()

    val exerciseRepository = ExerciseRepository(database.exerciseDao())
    val programRepository = ProgramRepository(
        database = database,
        programDao = database.programDao(),
        workoutTemplateDao = database.workoutTemplateDao(),
        workoutTemplateExerciseDao = database.workoutTemplateExerciseDao(),
        progressionStateDao = database.progressionStateDao(),
        exerciseDao = database.exerciseDao(),
    )
    val workoutSessionRepository = WorkoutSessionRepository(
        database = database,
        workoutSessionDao = database.workoutSessionDao(),
        sessionExerciseDao = database.sessionExerciseDao(),
        sessionSetDao = database.sessionSetDao(),
        programDao = database.programDao(),
        workoutTemplateDao = database.workoutTemplateDao(),
        workoutTemplateExerciseDao = database.workoutTemplateExerciseDao(),
        supersetGroupDao = database.supersetGroupDao(),
    )
}
