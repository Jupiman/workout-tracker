package com.jupiman.workouttracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jupiman.workouttracker.data.local.entity.SupersetGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupersetGroupDao {
    @Query("SELECT * FROM superset_groups WHERE workoutTemplateId = :workoutTemplateId")
    fun observeForWorkoutTemplate(workoutTemplateId: Long): Flow<List<SupersetGroupEntity>>

    @Query("SELECT * FROM superset_groups WHERE id = :id")
    suspend fun getById(id: Long): SupersetGroupEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: SupersetGroupEntity): Long

    @Update
    suspend fun update(group: SupersetGroupEntity)
}

