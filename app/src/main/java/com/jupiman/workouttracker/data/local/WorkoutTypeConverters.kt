package com.jupiman.workouttracker.data.local

import androidx.room.TypeConverter
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.entity.WarmupLoadType
import com.jupiman.workouttracker.data.local.entity.SelfHostedSyncState

class WorkoutTypeConverters {
    @TypeConverter
    fun toSelfHostedSyncState(value: String): SelfHostedSyncState = SelfHostedSyncState.valueOf(value)

    @TypeConverter
    fun fromSelfHostedSyncState(value: SelfHostedSyncState): String = value.name

    @TypeConverter
    fun toWarmupLoadType(value: String): WarmupLoadType = WarmupLoadType.valueOf(value)

    @TypeConverter
    fun fromWarmupLoadType(value: WarmupLoadType): String = value.name

    @TypeConverter
    fun toTrackingMode(value: String) = com.jupiman.workouttracker.data.local.entity.TrackingMode.valueOf(value)

    @TypeConverter
    fun fromTrackingMode(value: com.jupiman.workouttracker.data.local.entity.TrackingMode) = value.name
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
