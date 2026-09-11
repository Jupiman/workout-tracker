package com.jupiman.workouttracker.di

import android.content.Context
import androidx.room.Room
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import com.jupiman.workouttracker.data.repository.ExerciseRepository
import com.jupiman.workouttracker.data.repository.ProgramRepository
import com.jupiman.workouttracker.data.repository.WorkoutSessionRepository
import com.jupiman.workouttracker.notification.AndroidRestTimerScheduler

class AppContainer(context: Context) {
    private val restTimerScheduler = AndroidRestTimerScheduler(context.applicationContext)

    val database: WorkoutTrackerDatabase = Room.databaseBuilder(
        context.applicationContext,
        WorkoutTrackerDatabase::class.java,
        "workout_tracker.db",
    )
        .addMigrations(WorkoutTrackerDatabase.MIGRATION_1_2)
        .addMigrations(WorkoutTrackerDatabase.MIGRATION_2_3)
        .addMigrations(WorkoutTrackerDatabase.MIGRATION_3_4)
        .build()

    val exerciseRepository = ExerciseRepository(database.exerciseDao())
    val programRepository = ProgramRepository(
        database = database,
        programDao = database.programDao(),
        workoutTemplateDao = database.workoutTemplateDao(),
        workoutTemplateExerciseDao = database.workoutTemplateExerciseDao(),
        workoutTemplateSetTargetDao = database.workoutTemplateSetTargetDao(),
        workoutTemplateWarmupSetDao = database.workoutTemplateWarmupSetDao(),
        progressionStateDao = database.progressionStateDao(),
        supersetGroupDao = database.supersetGroupDao(),
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
        workoutTemplateSetTargetDao = database.workoutTemplateSetTargetDao(),
        workoutTemplateWarmupSetDao = database.workoutTemplateWarmupSetDao(),
        progressionStateDao = database.progressionStateDao(),
        supersetGroupDao = database.supersetGroupDao(),
        restTimerScheduler = restTimerScheduler,
    )
}
