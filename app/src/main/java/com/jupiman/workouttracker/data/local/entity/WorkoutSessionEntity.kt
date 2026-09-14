package com.jupiman.workouttracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class WorkoutSessionStatus {
    ACTIVE,
    COMPLETED,
    PARTIAL,
}

@Entity(
    tableName = "workout_sessions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["sourceProgramId"]),
        Index(value = ["sourceWorkoutTemplateId"]),
        Index(value = ["completedAt"]),
    ],
)
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceWorkoutTemplateId: Long?,
    val sourceProgramId: Long?,
    val programNameSnapshot: String,
    val workoutNameSnapshot: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val status: WorkoutSessionStatus = WorkoutSessionStatus.ACTIVE,
    val restEndsAt: Long? = null,
    val activeDurationSetId: Long? = null,
    val durationStartsAt: Long? = null,
    val durationEndsAt: Long? = null,
    val progressionApplied: Boolean = false,
)
