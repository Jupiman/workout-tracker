package com.jupiman.workouttracker.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AndroidDurationTimerScheduler(private val context: Context) : DurationTimerScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(durationEndsAt: Long) {
        val intent = pendingIntent(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, durationEndsAt, intent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, durationEndsAt, intent)
        }
    }

    override fun cancel() = alarmManager.cancel(pendingIntent(context))

    companion object {
        private const val REQUEST_CODE = 9002
        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, DurationTimerReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
