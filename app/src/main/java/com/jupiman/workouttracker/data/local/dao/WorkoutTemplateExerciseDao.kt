package com.jupiman.workouttracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutTemplateExerciseDao {
    @Query("SELECT * FROM workout_template_exercises WHERE workoutTemplateId = :workoutTemplateId ORDER BY sortOrder")
    fun observeForWorkoutTemplate(workoutTemplateId: Long): Flow<List<WorkoutTemplateExerciseEntity>>

    @Query("SELECT * FROM workout_template_exercises WHERE id = :id")
    suspend fun getById(id: Long): WorkoutTemplateExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exercise: WorkoutTemplateExerciseEntity): Long

    @Update
    suspend fun update(exercise: WorkoutTemplateExerciseEntity)
}

