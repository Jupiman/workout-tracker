package com.jupiman.workouttracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs WHERE archived = 0 ORDER BY createdAt DESC")
    fun observeAllActive(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM programs WHERE active = 1 AND archived = 0 LIMIT 1")
    fun observeActive(): Flow<ProgramEntity?>

    @Query("SELECT * FROM programs WHERE id = :id")
    suspend fun getById(id: Long): ProgramEntity?

    @Query("SELECT * FROM programs ORDER BY createdAt DESC")
    suspend fun getAll(): List<ProgramEntity>

    @Query("SELECT COUNT(*) FROM programs WHERE archived = 0")
    suspend fun activeProgramCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(program: ProgramEntity): Long

    @Update
    suspend fun update(program: ProgramEntity)

    @Query("UPDATE programs SET active = 0")
    suspend fun clearActivePrograms()

    @Query("UPDATE programs SET active = 1 WHERE id = :programId AND archived = 0")
    suspend fun setProgramActive(programId: Long)

    @Transaction
    suspend fun activate(programId: Long) {
        clearActivePrograms()
        setProgramActive(programId)
    }

    @Query("UPDATE programs SET archived = 1, active = 0 WHERE id = :id")
    suspend fun archive(id: Long)
}
