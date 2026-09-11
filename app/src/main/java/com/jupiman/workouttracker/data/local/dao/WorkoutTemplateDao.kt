package com.jupiman.workouttracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutTemplateDao {
    @Query("SELECT * FROM workout_templates WHERE programId = :programId ORDER BY sortOrder")
    fun observeForProgram(programId: Long): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_templates WHERE id = :id")
    suspend fun getById(id: Long): WorkoutTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(template: WorkoutTemplateEntity): Long

    @Update
    suspend fun update(template: WorkoutTemplateEntity)
}

