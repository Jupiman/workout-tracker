package com.jupiman.workouttracker.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jupiman.workouttracker.WorkoutTrackerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DurationTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val app = context.applicationContext as WorkoutTrackerApplication
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (app.container.workoutSessionRepository.reconcileDurationTimer()) {
                    app.container.workoutNotificationCoordinator.showDurationFinishedAlert()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
