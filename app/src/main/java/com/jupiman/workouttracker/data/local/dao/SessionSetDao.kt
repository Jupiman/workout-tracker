package com.jupiman.workouttracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionSetDao {
    @Query("SELECT * FROM session_sets WHERE sessionExerciseId = :sessionExerciseId ORDER BY setOrder")
    fun observeForSessionExercise(sessionExerciseId: Long): Flow<List<SessionSetEntity>>

    @Query("SELECT * FROM session_sets WHERE sessionExerciseId = :sessionExerciseId ORDER BY setOrder")
    suspend fun getForSessionExercise(sessionExerciseId: Long): List<SessionSetEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(set: SessionSetEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(sets: List<SessionSetEntity>)

    @Update
    suspend fun update(set: SessionSetEntity)
}

