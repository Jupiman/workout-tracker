package com.jupiman.workouttracker.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AndroidRestTimerScheduler(
    private val context: Context,
) : RestTimerScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(restEndsAt: Long) {
        val pendingIntent = restTimerPendingIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                restEndsAt,
                pendingIntent,
            )
        } else {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                restEndsAt,
                pendingIntent,
            )
        }
    }

    override fun cancel() {
        alarmManager.cancel(restTimerPendingIntent(context))
    }

    companion object {
        private const val REQUEST_CODE = 9001

        fun restTimerPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, RestTimerReceiver::class.java)
            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

