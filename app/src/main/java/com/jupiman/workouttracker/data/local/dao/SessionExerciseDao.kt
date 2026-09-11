package com.jupiman.workouttracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.jupiman.workouttracker.data.local.entity.SessionExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionExerciseDao {
    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY sortOrderSnapshot")
    fun observeForSession(sessionId: Long): Flow<List<SessionExerciseEntity>>

    @Query("SELECT * FROM session_exercises WHERE sessionId = :sessionId ORDER BY sortOrderSnapshot")
    suspend fun getForSession(sessionId: Long): List<SessionExerciseEntity>

    @Query("SELECT * FROM session_exercises WHERE id = :id")
    suspend fun getById(id: Long): SessionExerciseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exercise: SessionExerciseEntity): Long
}
