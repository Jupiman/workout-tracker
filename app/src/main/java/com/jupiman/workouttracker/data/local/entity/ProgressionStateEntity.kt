package com.jupiman.workouttracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "progression_states",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutTemplateExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProgressionStateEntity(
    @PrimaryKey val workoutTemplateExerciseId: Long,
    val currentWeightCentiKg: Int,
    val currentTargetReps: Int,
    val updatedAt: Long,
)

