package com.jupiman.workouttracker.di

import android.content.Context
import androidx.room.Room
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import com.jupiman.workouttracker.data.repository.DataBackupRepository
import com.jupiman.workouttracker.data.repository.ExerciseRepository
import com.jupiman.workouttracker.data.repository.ExerciseProgressRepository
import com.jupiman.workouttracker.data.repository.ProgramRepository
import com.jupiman.workouttracker.data.repository.ProgramTransferRepository
import com.jupiman.workouttracker.data.repository.WorkoutSessionRepository
import com.jupiman.workouttracker.notification.AndroidRestTimerScheduler
import com.jupiman.workouttracker.notification.AndroidDurationTimerScheduler
import com.jupiman.workouttracker.notification.AndroidWorkoutNotificationCoordinator
import com.jupiman.workouttracker.notification.WorkoutNotificationActionHandler
import com.jupiman.workouttracker.preferences.AppPreferencesRepository
import com.jupiman.workouttracker.healthconnect.AndroidHealthConnectGateway
import com.jupiman.workouttracker.healthconnect.FinalizedWorkoutSource
import com.jupiman.workouttracker.healthconnect.HealthConnectSyncManager
import com.jupiman.workouttracker.wear.AndroidWearWorkoutBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val appPreferencesRepository = AppPreferencesRepository.create(context)
    private val restTimerScheduler = AndroidRestTimerScheduler(context.applicationContext)
    private val durationTimerScheduler = AndroidDurationTimerScheduler(context.applicationContext)

    val database: WorkoutTrackerDatabase = Room.databaseBuilder(
        context.applicationContext,
        WorkoutTrackerDatabase::class.java,
        "workout_tracker.db",
    )
        .addCallback(WorkoutTrackerDatabase.SEED_DEFAULT_EXERCISES_ON_CREATE)
        .addMigrations(WorkoutTrackerDatabase.MIGRATION_1_2)
        .addMigrations(WorkoutTrackerDatabase.MIGRATION_2_3)
        .addMigrations(WorkoutTrackerDatabase.MIGRATION_3_4)
        .addMigrations(WorkoutTrackerDatabase.MIGRATION_4_5)
        .addMigrations(WorkoutTrackerDatabase.MIGRATION_5_6)
        .addMigrations(WorkoutTrackerDatabase.MIGRATION_6_7)
        .addMigrations(WorkoutTrackerDatabase.MIGRATION_7_8)
        .addMigrations(WorkoutTrackerDatabase.MIGRATION_8_9)
        .build()

    val workoutNotificationCoordinator = AndroidWorkoutNotificationCoordinator(
        context = context.applicationContext,
        workoutSessionDao = database.workoutSessionDao(),
        appPreferencesRepository = appPreferencesRepository,
    )

    val exerciseRepository = ExerciseRepository(database.exerciseDao())
    val exerciseProgressRepository = ExerciseProgressRepository(
        workoutTemplateExerciseDao = database.workoutTemplateExerciseDao(),
        workoutSessionDao = database.workoutSessionDao(),
    )
    val dataBackupRepository = DataBackupRepository(database)
    val programTransferRepository = ProgramTransferRepository(database)
    val healthConnectSyncManager = HealthConnectSyncManager(
        gateway = AndroidHealthConnectGateway(context.applicationContext),
        workoutSource = object : FinalizedWorkoutSource {
            override suspend fun finalizedWorkouts() =
                database.workoutSessionDao().getFinalizedForHealthConnect()

            override suspend fun finalizedWorkout(sessionId: Long) =
                database.workoutSessionDao().getById(sessionId)
        },
        isSyncEnabled = { appPreferencesRepository.current().healthConnectSyncEnabled },
        postWorkoutScope = applicationScope,
    )
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
        durationTimerScheduler = durationTimerScheduler,
        workoutNotificationUpdater = workoutNotificationCoordinator,
        durationPreparationProvider = appPreferencesRepository,
        finalizedWorkoutSync = healthConnectSyncManager,
    )

    val wearWorkoutBridge = AndroidWearWorkoutBridge(
        context = context.applicationContext,
        workoutSessionRepository = workoutSessionRepository,
        workoutSessionDao = database.workoutSessionDao(),
        appPreferencesRepository = appPreferencesRepository,
    )

    val workoutNotificationActionHandler = WorkoutNotificationActionHandler(
        workoutSessionRepository = workoutSessionRepository,
        workoutNotificationUpdater = workoutNotificationCoordinator,
    )
}
