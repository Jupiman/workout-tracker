package com.jupiman.workouttracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {
    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' LIMIT 1")
    fun observeActive(): Flow<WorkoutSessionEntity?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' LIMIT 1")
    fun observeActiveWithDetails(): Flow<WorkoutSessionWithDetails?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveWithDetails(): WorkoutSessionWithDetails?

    @Query("SELECT * FROM workout_sessions WHERE status != 'ACTIVE' ORDER BY completedAt DESC, startedAt DESC")
    fun observeHistory(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_sessions WHERE status != 'ACTIVE' AND completedAt IS NOT NULL ORDER BY startedAt ASC")
    suspend fun getFinalizedForHealthConnect(): List<WorkoutSessionEntity>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE status != 'ACTIVE' ORDER BY completedAt DESC, startedAt DESC")
    fun observeHistoryWithDetails(): Flow<List<WorkoutSessionWithDetails>>

    @Query(
        """
        SELECT ws.* FROM workout_sessions AS ws
        INNER JOIN programs AS p ON p.id = ws.sourceProgramId
        WHERE p.active = 1
            AND p.archived = 0
            AND ws.status != 'ACTIVE'
        ORDER BY ws.completedAt DESC, ws.startedAt DESC
        LIMIT 1
        """,
    )
    fun observeLatestFinishedForActiveProgram(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_sessions WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getActive(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getById(id: Long): WorkoutSessionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: WorkoutSessionEntity): Long

    @Update
    suspend fun update(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_sessions WHERE id = :id AND status = 'ACTIVE'")
    suspend fun deleteActiveById(id: Long)
}
