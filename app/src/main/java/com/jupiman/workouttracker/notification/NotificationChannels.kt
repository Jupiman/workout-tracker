package com.jupiman.workouttracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

const val REST_TIMER_CHANNEL_ID = "rest_timer"
const val WORKOUT_CONTROLS_CHANNEL_ID = "workout_controls"

fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val restChannel = NotificationChannel(
        REST_TIMER_CHANNEL_ID,
        "Rest timer",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "Rest timer completion alerts"
        enableVibration(true)
    }
    val workoutControlsChannel = NotificationChannel(
        WORKOUT_CONTROLS_CHANNEL_ID,
        "Workout controls",
        NotificationManager.IMPORTANCE_LOW,
    ).apply {
        description = "Ongoing workout set and rest controls"
        setSound(null, null)
        enableVibration(false)
    }

    context.getSystemService(NotificationManager::class.java)
        .createNotificationChannels(listOf(restChannel, workoutControlsChannel))
}
