package com.jupiman.workouttracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_template_warmup_sets",
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
        Index(value = ["workoutTemplateExerciseId", "sortOrder"], unique = true),
    ],
)
data class WorkoutTemplateWarmupSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutTemplateExerciseId: Long,
    val sortOrder: Int,
    val reps: Int,
    val percentOfWorkingWeight: Int,
)
