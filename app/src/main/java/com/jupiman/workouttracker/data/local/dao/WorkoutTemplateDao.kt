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

    @Query(
        """
        SELECT wt.* FROM workout_templates AS wt
        INNER JOIN programs AS p ON p.id = wt.programId
        WHERE p.active = 1 AND p.archived = 0
        ORDER BY wt.sortOrder
        LIMIT 1
        """,
    )
    fun observeFirstForActiveProgram(): Flow<WorkoutTemplateEntity?>

    @Query(
        """
        SELECT wt.* FROM workout_templates AS wt
        INNER JOIN programs AS p ON p.id = wt.programId
        WHERE p.active = 1 AND p.archived = 0
        ORDER BY wt.sortOrder
        """,
    )
    fun observeForActiveProgram(): Flow<List<WorkoutTemplateEntity>>

    @Query("SELECT * FROM workout_templates WHERE programId = :programId ORDER BY sortOrder")
    suspend fun getForProgram(programId: Long): List<WorkoutTemplateEntity>

    @Query("SELECT * FROM workout_templates WHERE id = :id")
    suspend fun getById(id: Long): WorkoutTemplateEntity?

    @Query("SELECT COUNT(*) FROM workout_templates WHERE programId = :programId")
    suspend fun countForProgram(programId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(template: WorkoutTemplateEntity): Long

    @Update
    suspend fun update(template: WorkoutTemplateEntity)

    @Query("DELETE FROM workout_templates WHERE id = :id")
    suspend fun deleteById(id: Long)
}
