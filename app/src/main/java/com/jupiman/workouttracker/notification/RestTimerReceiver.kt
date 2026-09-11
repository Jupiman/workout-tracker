package com.jupiman.workouttracker.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jupiman.workouttracker.MainActivity
import com.jupiman.workouttracker.R

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        createNotificationChannels(context)

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, REST_TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rest_timer)
            .setContentTitle("Rest finished")
            .setContentText("Time for the next set.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(REST_TIMER_NOTIFICATION_ID, notification)
    }

    private companion object {
        const val REST_TIMER_NOTIFICATION_ID = 1001
    }
}
