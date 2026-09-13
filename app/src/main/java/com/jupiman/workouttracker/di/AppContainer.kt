package com.jupiman.workouttracker.di

import android.content.Context
import androidx.room.Room
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import com.jupiman.workouttracker.data.repository.DataBackupRepository
import com.jupiman.workouttracker.data.repository.ExerciseRepository
import com.jupiman.workouttracker.data.repository.ProgramRepository
import com.jupiman.workouttracker.data.repository.WorkoutSessionRepository
import com.jupiman.workouttracker.notification.AndroidRestTimerScheduler
import com.jupiman.workouttracker.notification.AndroidWorkoutNotificationCoordinator
import com.jupiman.workouttracker.notification.WorkoutNotificationActionHandler
import com.jupiman.workouttracker.wear.AndroidWearWorkoutBridge

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
        .addMigrations(WorkoutTrackerDatabase.MIGRATION_4_5)
        .build()

    val workoutNotificationCoordinator = AndroidWorkoutNotificationCoordinator(
        context = context.applicationContext,
        workoutSessionDao = database.workoutSessionDao(),
    )

    val exerciseRepository = ExerciseRepository(database.exerciseDao())
    val dataBackupRepository = DataBackupRepository(database)
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
        workoutNotificationUpdater = workoutNotificationCoordinator,
    )

    val wearWorkoutBridge = AndroidWearWorkoutBridge(
        context = context.applicationContext,
        workoutSessionRepository = workoutSessionRepository,
        workoutSessionDao = database.workoutSessionDao(),
    )

    val workoutNotificationActionHandler = WorkoutNotificationActionHandler(
        workoutSessionRepository = workoutSessionRepository,
        workoutNotificationUpdater = workoutNotificationCoordinator,
    )
}
