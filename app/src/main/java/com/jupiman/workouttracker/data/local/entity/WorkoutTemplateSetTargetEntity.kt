package com.jupiman.workouttracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_template_set_targets",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutTemplateExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["workoutTemplateExerciseId"]),
        Index(value = ["workoutTemplateExerciseId", "setOrder"], unique = true),
    ],
)
data class WorkoutTemplateSetTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutTemplateExerciseId: Long,
    val setOrder: Int,
    val prescribedWeightCentiKg: Int,
    val prescribedReps: Int,
    val countsForProgression: Boolean = true,
)
