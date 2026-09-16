package com.jupiman.workouttracker.data.local

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.jupiman.workouttracker.data.local.dao.ExerciseDao
import com.jupiman.workouttracker.data.local.dao.ProgramDao
import com.jupiman.workouttracker.data.local.dao.ProgressionStateDao
import com.jupiman.workouttracker.data.local.dao.SessionExerciseDao
import com.jupiman.workouttracker.data.local.dao.SessionSetDao
import com.jupiman.workouttracker.data.local.dao.SupersetGroupDao
import com.jupiman.workouttracker.data.local.dao.WorkoutSessionDao
import com.jupiman.workouttracker.data.local.dao.WorkoutTemplateDao
import com.jupiman.workouttracker.data.local.dao.WorkoutTemplateExerciseDao
import com.jupiman.workouttracker.data.local.dao.WorkoutTemplateSetTargetDao
import com.jupiman.workouttracker.data.local.dao.WorkoutTemplateWarmupSetDao
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import com.jupiman.workouttracker.data.local.entity.ProgressionStateEntity
import com.jupiman.workouttracker.data.local.entity.SessionExerciseEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SupersetGroupEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateExerciseEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateSetTargetEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateWarmupSetEntity
import java.util.UUID

@Database(
    entities = [
        ExerciseEntity::class,
        ProgramEntity::class,
        WorkoutTemplateEntity::class,
        SupersetGroupEntity::class,
        WorkoutTemplateExerciseEntity::class,
        ProgressionStateEntity::class,
        WorkoutTemplateSetTargetEntity::class,
        WorkoutTemplateWarmupSetEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SessionSetEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
@TypeConverters(WorkoutTypeConverters::class)
abstract class WorkoutTrackerDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun programDao(): ProgramDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun supersetGroupDao(): SupersetGroupDao
    abstract fun workoutTemplateExerciseDao(): WorkoutTemplateExerciseDao
    abstract fun workoutTemplateSetTargetDao(): WorkoutTemplateSetTargetDao
    abstract fun workoutTemplateWarmupSetDao(): WorkoutTemplateWarmupSetDao
    abstract fun progressionStateDao(): ProgressionStateDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun sessionExerciseDao(): SessionExerciseDao
    abstract fun sessionSetDao(): SessionSetDao

    companion object {
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_template_exercises ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN selfHostedSyncState TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN selfHostedLastAttemptAt INTEGER")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN selfHostedLastError TEXT")
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN sourceProgressionTrackSyncId TEXT")
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN resultingProgressionWeightCentiKg INTEGER")
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN resultingProgressionTargetReps INTEGER")
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN resultingProgressionDurationSeconds INTEGER")
                db.execSQL("ALTER TABLE session_sets ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")

                val progressionIds = mutableMapOf<Long, String>()
                db.query("SELECT id FROM workout_template_exercises").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val syncId = UUID.randomUUID().toString()
                        progressionIds[id] = syncId
                        db.execSQL(
                            "UPDATE workout_template_exercises SET syncId = ? WHERE id = ?",
                            arrayOf(syncId, id),
                        )
                    }
                }
                db.query("SELECT id FROM workout_sessions").use { cursor ->
                    while (cursor.moveToNext()) {
                        db.execSQL(
                            "UPDATE workout_sessions SET syncId = ? WHERE id = ?",
                            arrayOf(UUID.randomUUID().toString(), cursor.getLong(0)),
                        )
                    }
                }
                val historicalProgressionIds = mutableMapOf<Long, String>()
                db.query("SELECT id, sourceWorkoutTemplateExerciseId FROM session_exercises").use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(0)
                        val sourceId = if (cursor.isNull(1)) null else cursor.getLong(1)
                        val sourceSyncId = sourceId?.let { source ->
                            progressionIds[source] ?: historicalProgressionIds.getOrPut(source) {
                                UUID.randomUUID().toString()
                            }
                        }
                        db.execSQL(
                            "UPDATE session_exercises SET syncId = ?, sourceProgressionTrackSyncId = ? WHERE id = ?",
                            arrayOf(UUID.randomUUID().toString(), sourceSyncId, id),
                        )
                    }
                }
                db.query("SELECT id FROM session_sets").use { cursor ->
                    while (cursor.moveToNext()) {
                        db.execSQL(
                            "UPDATE session_sets SET syncId = ? WHERE id = ?",
                            arrayOf(UUID.randomUUID().toString(), cursor.getLong(0)),
                        )
                    }
                }

                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_workout_template_exercises_syncId ON workout_template_exercises (syncId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_workout_sessions_syncId ON workout_sessions (syncId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_selfHostedSyncState ON workout_sessions (selfHostedSyncState)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_session_exercises_syncId ON session_exercises (syncId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_session_sets_syncId ON session_sets (syncId)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE workout_template_exercises " +
                        "ADD COLUMN warmupRoundingCentiKg INTEGER NOT NULL DEFAULT 500",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workout_template_warmup_sets_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workoutTemplateExerciseId INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        reps INTEGER NOT NULL,
                        loadType TEXT NOT NULL,
                        percentOfWorkingWeight INTEGER,
                        fixedWeightCentiKg INTEGER,
                        FOREIGN KEY(workoutTemplateExerciseId)
                            REFERENCES workout_template_exercises(id)
                            ON UPDATE NO ACTION
                            ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO workout_template_warmup_sets_new (
                        id, workoutTemplateExerciseId, sortOrder, reps, loadType,
                        percentOfWorkingWeight, fixedWeightCentiKg
                    )
                    SELECT id, workoutTemplateExerciseId, sortOrder, reps, 'PERCENTAGE',
                        percentOfWorkingWeight, NULL
                    FROM workout_template_warmup_sets
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE workout_template_warmup_sets")
                db.execSQL("ALTER TABLE workout_template_warmup_sets_new RENAME TO workout_template_warmup_sets")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_workout_template_warmup_sets_workoutTemplateExerciseId " +
                        "ON workout_template_warmup_sets (workoutTemplateExerciseId)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_workout_template_warmup_sets_workoutTemplateExerciseId_sortOrder " +
                        "ON workout_template_warmup_sets (workoutTemplateExerciseId, sortOrder)",
                )
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_template_exercises ADD COLUMN trackingMode TEXT NOT NULL DEFAULT 'WEIGHT_REPS'")
                db.execSQL("ALTER TABLE workout_template_exercises ADD COLUMN targetDurationSeconds INTEGER")
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN trackingModeSnapshot TEXT NOT NULL DEFAULT 'WEIGHT_REPS'")
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN targetDurationSecondsSnapshot INTEGER")
                db.execSQL("ALTER TABLE session_sets ADD COLUMN prescribedDurationSeconds INTEGER")
                db.execSQL("ALTER TABLE session_sets ADD COLUMN actualDurationSeconds INTEGER")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_template_exercises ADD COLUMN durationIncrementSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE session_exercises ADD COLUMN durationIncrementSecondsSnapshot INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN activeDurationSetId INTEGER")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN durationStartsAt INTEGER")
                db.execSQL("ALTER TABLE workout_sessions ADD COLUMN durationEndsAt INTEGER")
            }
        }
        val SEED_DEFAULT_EXERCISES_ON_CREATE = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                DefaultExerciseSeeder.seed(db)
            }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_workout_templates_programId_sortOrder")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_workout_templates_programId_sortOrder " +
                        "ON workout_templates (programId, sortOrder)",
                )
                db.execSQL("DROP INDEX IF EXISTS index_workout_template_exercises_workoutTemplateId_sortOrder")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_workout_template_exercises_workoutTemplateId_sortOrder " +
                        "ON workout_template_exercises (workoutTemplateId, sortOrder)",
                )
                db.execSQL("DROP INDEX IF EXISTS index_session_exercises_sessionId_sortOrderSnapshot")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_session_exercises_sessionId_sortOrderSnapshot " +
                        "ON session_exercises (sessionId, sortOrderSnapshot)",
                )
                db.execSQL("DROP INDEX IF EXISTS index_session_sets_sessionExerciseId_setOrder")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_session_sets_sessionExerciseId_setOrder " +
                        "ON session_sets (sessionExerciseId, setOrder)",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workout_template_set_targets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workoutTemplateExerciseId INTEGER NOT NULL,
                        setOrder INTEGER NOT NULL,
                        prescribedWeightCentiKg INTEGER NOT NULL,
                        prescribedReps INTEGER NOT NULL,
                        countsForProgression INTEGER NOT NULL,
                        FOREIGN KEY(workoutTemplateExerciseId)
                            REFERENCES workout_template_exercises(id)
                            ON UPDATE NO ACTION
                            ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_workout_template_set_targets_workoutTemplateExerciseId " +
                        "ON workout_template_set_targets (workoutTemplateExerciseId)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_workout_template_set_targets_workoutTemplateExerciseId_setOrder " +
                        "ON workout_template_set_targets (workoutTemplateExerciseId, setOrder)",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workout_template_warmup_sets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workoutTemplateExerciseId INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        reps INTEGER NOT NULL,
                        percentOfWorkingWeight INTEGER NOT NULL,
                        FOREIGN KEY(workoutTemplateExerciseId)
                            REFERENCES workout_template_exercises(id)
                            ON UPDATE NO ACTION
                            ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_workout_template_warmup_sets_workoutTemplateExerciseId " +
                        "ON workout_template_warmup_sets (workoutTemplateExerciseId)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS " +
                        "index_workout_template_warmup_sets_workoutTemplateExerciseId_sortOrder " +
                        "ON workout_template_warmup_sets (workoutTemplateExerciseId, sortOrder)",
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE workout_template_exercises " +
                        "ADD COLUMN setupNote TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE session_exercises " +
                        "ADD COLUMN setupNoteSnapshot TEXT NOT NULL DEFAULT ''",
                )
            }
        }
    }
}
