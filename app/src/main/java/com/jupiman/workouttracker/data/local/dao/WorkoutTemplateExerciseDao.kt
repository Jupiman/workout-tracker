package com.jupiman.workouttracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateExerciseEntity
import com.jupiman.workouttracker.data.local.model.WorkoutTemplateExerciseEditorItem
import com.jupiman.workouttracker.data.local.model.ExerciseProgressTrack
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutTemplateExerciseDao {
    @Query(
        """
        SELECT
            wte.id AS workoutTemplateExerciseId,
            wte.exerciseId AS exerciseId,
            e.name AS exerciseName,
            p.id AS programId,
            p.name AS programName,
            wt.id AS workoutTemplateId,
            wt.name AS workoutName,
            wte.plannedWorkingSets AS plannedWorkingSets,
            wte.repMin AS repMin,
            wte.repMax AS repMax,
            wte.trackingMode AS trackingMode,
            ps.currentWeightCentiKg AS currentWeightCentiKg,
            ps.currentTargetReps AS currentTargetReps,
            wte.targetDurationSeconds AS targetDurationSeconds
        FROM workout_template_exercises AS wte
        INNER JOIN exercises AS e ON e.id = wte.exerciseId
        INNER JOIN workout_templates AS wt ON wt.id = wte.workoutTemplateId
        INNER JOIN programs AS p ON p.id = wt.programId
        INNER JOIN progression_states AS ps ON ps.workoutTemplateExerciseId = wte.id
        WHERE wte.exerciseId = :exerciseId
        ORDER BY p.active DESC, p.name COLLATE NOCASE, wt.sortOrder, wte.sortOrder
        """,
    )
    fun observeProgressTracksForExercise(exerciseId: Long): Flow<List<ExerciseProgressTrack>>

    @Query("SELECT * FROM workout_template_exercises WHERE workoutTemplateId = :workoutTemplateId ORDER BY sortOrder")
    fun observeForWorkoutTemplate(workoutTemplateId: Long): Flow<List<WorkoutTemplateExerciseEntity>>

    @Query(
        """
        SELECT 
            wte.id,
            wte.workoutTemplateId,
            wte.exerciseId,
            e.name AS exerciseName,
            wte.sortOrder,
            wte.plannedWorkingSets,
            wte.repMin,
            wte.repMax,
            wte.incrementCentiKg,
            wte.restSeconds,
            wte.setupNote,
            wte.trackingMode,
            wte.targetDurationSeconds,
            wte.durationIncrementSeconds,
            wte.supersetGroupId,
            ps.currentWeightCentiKg,
            ps.currentTargetReps
        FROM workout_template_exercises AS wte
        INNER JOIN exercises AS e ON e.id = wte.exerciseId
        INNER JOIN progression_states AS ps ON ps.workoutTemplateExerciseId = wte.id
        WHERE wte.workoutTemplateId = :workoutTemplateId
        ORDER BY wte.sortOrder
        """,
    )
    fun observeEditorItemsForWorkoutTemplate(workoutTemplateId: Long): Flow<List<WorkoutTemplateExerciseEditorItem>>

    @Query("SELECT * FROM workout_template_exercises WHERE workoutTemplateId = :workoutTemplateId ORDER BY sortOrder")
    suspend fun getForWorkoutTemplate(workoutTemplateId: Long): List<WorkoutTemplateExerciseEntity>

    @Query(
        """
        SELECT 
            wte.id,
            wte.workoutTemplateId,
            wte.exerciseId,
            e.name AS exerciseName,
            wte.sortOrder,
            wte.plannedWorkingSets,
            wte.repMin,
            wte.repMax,
            wte.incrementCentiKg,
            wte.restSeconds,
            wte.setupNote,
            wte.trackingMode,
            wte.targetDurationSeconds,
            wte.durationIncrementSeconds,
            wte.supersetGroupId,
            ps.currentWeightCentiKg,
            ps.currentTargetReps
        FROM workout_template_exercises AS wte
        INNER JOIN exercises AS e ON e.id = wte.exerciseId
        INNER JOIN progression_states AS ps ON ps.workoutTemplateExerciseId = wte.id
        WHERE wte.workoutTemplateId = :workoutTemplateId
        ORDER BY wte.sortOrder
        """,
    )
    suspend fun getEditorItemsForWorkoutTemplate(workoutTemplateId: Long): List<WorkoutTemplateExerciseEditorItem>

    @Query("SELECT * FROM workout_template_exercises WHERE id = :id")
    suspend fun getById(id: Long): WorkoutTemplateExerciseEntity?

    @Query("SELECT COUNT(*) FROM workout_template_exercises WHERE workoutTemplateId = :workoutTemplateId")
    suspend fun countForWorkoutTemplate(workoutTemplateId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exercise: WorkoutTemplateExerciseEntity): Long

    @Update
    suspend fun update(exercise: WorkoutTemplateExerciseEntity)

    @Query("DELETE FROM workout_template_exercises WHERE id = :id")
    suspend fun deleteById(id: Long)
}
