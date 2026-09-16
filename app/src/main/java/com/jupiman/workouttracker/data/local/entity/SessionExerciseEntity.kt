package com.jupiman.workouttracker.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "session_exercises",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sourceWorkoutTemplateExerciseId"]),
        Index(value = ["sessionId", "sortOrderSnapshot"]),
        Index(value = ["syncId"], unique = true),
    ],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sourceWorkoutTemplateExerciseId: Long?,
    val exerciseNameSnapshot: String,
    val sortOrderSnapshot: Int,
    val plannedSetCountSnapshot: Int,
    val repMinSnapshot: Int,
    val repMaxSnapshot: Int,
    val targetRepsSnapshot: Int,
    val prescribedWeightCentiKgSnapshot: Int,
    val incrementCentiKgSnapshot: Int,
    val restSecondsSnapshot: Int,
    val setupNoteSnapshot: String,
    val supersetGroupSnapshot: Long?,
    val supersetRestSecondsSnapshot: Int?,
    val trackingModeSnapshot: TrackingMode = TrackingMode.WEIGHT_REPS,
    val targetDurationSecondsSnapshot: Int? = null,
    val durationIncrementSecondsSnapshot: Int = 0,
    @ColumnInfo(defaultValue = "''") val syncId: String = newSyncId(),
    val sourceProgressionTrackSyncId: String? = null,
    val resultingProgressionWeightCentiKg: Int? = null,
    val resultingProgressionTargetReps: Int? = null,
    val resultingProgressionDurationSeconds: Int? = null,
)
