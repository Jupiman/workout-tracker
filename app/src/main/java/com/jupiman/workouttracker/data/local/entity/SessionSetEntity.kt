package com.jupiman.workouttracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SetType {
    WARMUP,
    WORKING,
    EXTRA,
    AMRAP,
    DROP,
}

enum class SessionSetStatus {
    PENDING,
    COMPLETED,
    SKIPPED,
}

@Entity(
    tableName = "session_sets",
    foreignKeys = [
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionExerciseId"]),
        Index(value = ["sessionExerciseId", "setOrder"]),
    ],
)
data class SessionSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val setOrder: Int,
    val setType: SetType,
    val isPlanned: Boolean,
    val countsForProgression: Boolean,
    val prescribedWeightCentiKg: Int?,
    val prescribedReps: Int?,
    val actualWeightCentiKg: Int? = null,
    val actualReps: Int? = null,
    val status: SessionSetStatus = SessionSetStatus.PENDING,
    val completedAt: Long? = null,
    val prescribedDurationSeconds: Int? = null,
    val actualDurationSeconds: Int? = null,
)
