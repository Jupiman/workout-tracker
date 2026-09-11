package com.jupiman.workouttracker.data.local

import androidx.room.TypeConverter
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus

class WorkoutTypeConverters {
    @TypeConverter
    fun toWorkoutSessionStatus(value: String): WorkoutSessionStatus = WorkoutSessionStatus.valueOf(value)

    @TypeConverter
    fun fromWorkoutSessionStatus(value: WorkoutSessionStatus): String = value.name

    @TypeConverter
    fun toSessionSetStatus(value: String): SessionSetStatus = SessionSetStatus.valueOf(value)

    @TypeConverter
    fun fromSessionSetStatus(value: SessionSetStatus): String = value.name

    @TypeConverter
    fun toSetType(value: String): SetType = SetType.valueOf(value)

    @TypeConverter
    fun fromSetType(value: SetType): String = value.name
}

