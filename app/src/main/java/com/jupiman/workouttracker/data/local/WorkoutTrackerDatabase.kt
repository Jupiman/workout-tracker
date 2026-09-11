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
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import com.jupiman.workouttracker.data.local.entity.ProgressionStateEntity
import com.jupiman.workouttracker.data.local.entity.SessionExerciseEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SupersetGroupEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateExerciseEntity

@Database(
    entities = [
        ExerciseEntity::class,
        ProgramEntity::class,
        WorkoutTemplateEntity::class,
        SupersetGroupEntity::class,
        WorkoutTemplateExerciseEntity::class,
        ProgressionStateEntity::class,
        WorkoutSessionEntity::class,
        SessionExerciseEntity::class,
        SessionSetEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(WorkoutTypeConverters::class)
abstract class WorkoutTrackerDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun programDao(): ProgramDao
    abstract fun workoutTemplateDao(): WorkoutTemplateDao
    abstract fun supersetGroupDao(): SupersetGroupDao
    abstract fun workoutTemplateExerciseDao(): WorkoutTemplateExerciseDao
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
    }
}
