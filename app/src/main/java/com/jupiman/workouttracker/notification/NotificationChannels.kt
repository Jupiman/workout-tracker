package com.jupiman.workouttracker.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings

const val REST_TIMER_CHANNEL_ID = "rest_timer"
const val REST_TIMER_ALERT_CHANNEL_ID = "rest_timer_alerts_v2"
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
    val restAlertChannel = NotificationChannel(
        REST_TIMER_ALERT_CHANNEL_ID,
        "Rest alerts",
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = "Rest completion alerts for phone and watch"
        enableVibration(true)
        vibrationPattern = longArrayOf(0, 250, 160, 250)
        setSound(
            Settings.System.DEFAULT_NOTIFICATION_URI,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
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
        .createNotificationChannels(listOf(restChannel, restAlertChannel, workoutControlsChannel))
}
