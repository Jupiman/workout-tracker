package com.jupiman.workouttracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jupiman.workouttracker.WorkoutTrackerApplication

class RestTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        createNotificationChannels(context)
        val app = context.applicationContext as WorkoutTrackerApplication
        app.container.workoutNotificationCoordinator.showRestFinishedAlert()
    }
}
