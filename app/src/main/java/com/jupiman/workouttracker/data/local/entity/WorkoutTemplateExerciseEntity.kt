package com.jupiman.workouttracker.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_template_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutTemplateId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = SupersetGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["supersetGroupId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["workoutTemplateId"]),
        Index(value = ["exerciseId"]),
        Index(value = ["supersetGroupId"]),
        Index(value = ["workoutTemplateId", "sortOrder"]),
        Index(value = ["syncId"], unique = true),
    ],
)
data class WorkoutTemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutTemplateId: Long,
    val exerciseId: Long,
    val sortOrder: Int,
    val plannedWorkingSets: Int,
    val repMin: Int,
    val repMax: Int,
    val incrementCentiKg: Int,
    val restSeconds: Int,
    val setupNote: String = "",
    val supersetGroupId: Long? = null,
    val trackingMode: TrackingMode = TrackingMode.WEIGHT_REPS,
    val targetDurationSeconds: Int? = null,
    val durationIncrementSeconds: Int = 0,
    val warmupRoundingCentiKg: Int = 500,
    @ColumnInfo(defaultValue = "''") val syncId: String = newSyncId(),
)
