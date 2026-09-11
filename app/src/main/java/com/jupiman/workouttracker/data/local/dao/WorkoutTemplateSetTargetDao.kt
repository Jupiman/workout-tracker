package com.jupiman.workouttracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateSetTargetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutTemplateSetTargetDao {
    @Query(
        "SELECT * FROM workout_template_set_targets " +
            "WHERE workoutTemplateExerciseId = :workoutTemplateExerciseId ORDER BY setOrder",
    )
    fun observeForTemplateExercise(workoutTemplateExerciseId: Long): Flow<List<WorkoutTemplateSetTargetEntity>>

    @Query(
        "SELECT * FROM workout_template_set_targets " +
            "WHERE workoutTemplateExerciseId = :workoutTemplateExerciseId ORDER BY setOrder",
    )
    suspend fun getForTemplateExercise(workoutTemplateExerciseId: Long): List<WorkoutTemplateSetTargetEntity>

    @Query(
        "SELECT * FROM workout_template_set_targets " +
            "WHERE workoutTemplateExerciseId = :workoutTemplateExerciseId AND setOrder = :setOrder",
    )
    suspend fun getForTemplateExerciseSetOrder(
        workoutTemplateExerciseId: Long,
        setOrder: Int,
    ): WorkoutTemplateSetTargetEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(target: WorkoutTemplateSetTargetEntity): Long

    @Update
    suspend fun update(target: WorkoutTemplateSetTargetEntity)

    @Query("DELETE FROM workout_template_set_targets WHERE workoutTemplateExerciseId = :workoutTemplateExerciseId")
    suspend fun deleteForTemplateExercise(workoutTemplateExerciseId: Long)

    @Query(
        "DELETE FROM workout_template_set_targets " +
            "WHERE workoutTemplateExerciseId = :workoutTemplateExerciseId AND setOrder = :setOrder",
    )
    suspend fun deleteForTemplateExerciseSetOrder(workoutTemplateExerciseId: Long, setOrder: Int)

    @Query(
        "DELETE FROM workout_template_set_targets " +
            "WHERE workoutTemplateExerciseId = :workoutTemplateExerciseId AND setOrder >= :setOrder",
    )
    suspend fun deleteFromSetOrder(workoutTemplateExerciseId: Long, setOrder: Int)
}
