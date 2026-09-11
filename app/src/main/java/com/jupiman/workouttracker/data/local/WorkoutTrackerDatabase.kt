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
    version = 4,
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
    }
}
