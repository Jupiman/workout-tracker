package com.jupiman.workouttracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateWarmupSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutTemplateWarmupSetDao {
    @Query(
        "SELECT * FROM workout_template_warmup_sets " +
            "WHERE workoutTemplateExerciseId = :workoutTemplateExerciseId ORDER BY sortOrder",
    )
    fun observeForTemplateExercise(workoutTemplateExerciseId: Long): Flow<List<WorkoutTemplateWarmupSetEntity>>

    @Query(
        "SELECT * FROM workout_template_warmup_sets " +
            "WHERE workoutTemplateExerciseId = :workoutTemplateExerciseId ORDER BY sortOrder",
    )
    suspend fun getForTemplateExercise(workoutTemplateExerciseId: Long): List<WorkoutTemplateWarmupSetEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(sets: List<WorkoutTemplateWarmupSetEntity>)

    @Query("DELETE FROM workout_template_warmup_sets WHERE workoutTemplateExerciseId = :workoutTemplateExerciseId")
    suspend fun deleteForTemplateExercise(workoutTemplateExerciseId: Long)
}
