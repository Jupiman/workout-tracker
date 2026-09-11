package com.jupiman.workouttracker.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "superset_groups",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutTemplateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["workoutTemplateId"])],
)
data class SupersetGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutTemplateId: Long,
    val restSeconds: Int,
)

