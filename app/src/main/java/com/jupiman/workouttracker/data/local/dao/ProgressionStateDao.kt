package com.jupiman.workouttracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jupiman.workouttracker.data.local.entity.ProgressionStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressionStateDao {
    @Query("SELECT * FROM progression_states WHERE workoutTemplateExerciseId = :workoutTemplateExerciseId")
    fun observeForTemplateExercise(workoutTemplateExerciseId: Long): Flow<ProgressionStateEntity?>

    @Query("SELECT * FROM progression_states WHERE workoutTemplateExerciseId = :workoutTemplateExerciseId")
    suspend fun getForTemplateExercise(workoutTemplateExerciseId: Long): ProgressionStateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(state: ProgressionStateEntity)

    @Update
    suspend fun update(state: ProgressionStateEntity)
}

