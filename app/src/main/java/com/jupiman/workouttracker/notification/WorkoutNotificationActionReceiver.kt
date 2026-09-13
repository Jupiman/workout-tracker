package com.jupiman.workouttracker.notification

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jupiman.workouttracker.WorkoutTrackerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WorkoutNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val app = context.applicationContext as WorkoutTrackerApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.container.workoutNotificationActionHandler.handle(intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE_SET = "com.jupiman.workouttracker.action.COMPLETE_SET"
        const val ACTION_ADD_REST = "com.jupiman.workouttracker.action.ADD_REST"
        const val ACTION_SKIP_REST = "com.jupiman.workouttracker.action.SKIP_REST"

        private const val EXTRA_SESSION_ID = "sessionId"
        private const val EXTRA_SET_ID = "setId"

        fun pendingIntent(
            context: Context,
            action: String,
            requestCode: Int,
            sessionId: Long? = null,
            setId: Long? = null,
        ): PendingIntent {
            val intent = Intent(context, WorkoutNotificationActionReceiver::class.java)
                .setAction(action)
            if (sessionId != null) intent.putExtra(EXTRA_SESSION_ID, sessionId)
            if (setId != null) intent.putExtra(EXTRA_SET_ID, setId)
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun sessionId(intent: Intent): Long = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        fun setId(intent: Intent): Long = intent.getLongExtra(EXTRA_SET_ID, -1L)
    }
}

class WorkoutNotificationActionHandler(
    private val workoutSessionRepository: com.jupiman.workouttracker.data.repository.WorkoutSessionRepository,
    private val workoutNotificationUpdater: WorkoutNotificationUpdater,
) {
    suspend fun handle(intent: Intent?) {
        when (intent?.action) {
            WorkoutNotificationActionReceiver.ACTION_COMPLETE_SET -> {
                val sessionId = WorkoutNotificationActionReceiver.sessionId(intent)
                val setId = WorkoutNotificationActionReceiver.setId(intent)
                if (sessionId > 0 && setId > 0) {
                    workoutSessionRepository.completeSetFromNotification(
                        expectedSessionId = sessionId,
                        setId = setId,
                    )
                }
            }
            WorkoutNotificationActionReceiver.ACTION_ADD_REST -> {
                runCatching { workoutSessionRepository.addRestTime(30) }
            }
            WorkoutNotificationActionReceiver.ACTION_SKIP_REST -> {
                workoutSessionRepository.skipRest()
            }
        }
        workoutNotificationUpdater.refresh()
    }
}
