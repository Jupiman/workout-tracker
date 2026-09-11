package com.jupiman.workouttracker.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "programs",
    indices = [Index(value = ["active"])],
)
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val active: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long,
)

