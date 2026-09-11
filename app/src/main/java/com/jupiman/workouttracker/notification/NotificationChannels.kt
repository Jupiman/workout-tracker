package com.jupiman.workouttracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

const val REST_TIMER_CHANNEL_ID = "rest_timer"

fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val channel = NotificationChannel(
        REST_TIMER_CHANNEL_ID,
        "Rest timer",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "Rest timer completion alerts"
        enableVibration(true)
    }

    context.getSystemService(NotificationManager::class.java)
        .createNotificationChannel(channel)
}

