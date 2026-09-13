package com.jupiman.workouttracker.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.Wearable
import com.jupiman.workouttracker.MainActivity
import com.jupiman.workouttracker.R
import com.jupiman.workouttracker.data.local.dao.WorkoutSessionDao
import com.jupiman.workouttracker.wear.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AndroidWorkoutNotificationCoordinator(
    private val context: Context,
    private val workoutSessionDao: WorkoutSessionDao,
) : WorkoutNotificationUpdater {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun refresh() {
        scope.launch {
            showCurrentState()
        }
    }

    override fun cancel() {
        notificationManager.cancel(WORKOUT_NOTIFICATION_ID)
    }

    suspend fun showCurrentState() {
        val activeWorkout = workoutSessionDao.getActiveWithDetails()
        val state = WorkoutNotificationProjector.stateFor(activeWorkout)
        if (state == null) {
            cancel()
            return
        }
        if (!canPostNotifications()) return
        notificationManager.notify(WORKOUT_NOTIFICATION_ID, buildNotification(state))
    }

    fun showRestFinishedAlert() {
        scope.launch {
            val activeWorkout = workoutSessionDao.getActiveWithDetails()
            val state = WorkoutNotificationProjector.stateFor(activeWorkout)
            val restFinishedState = state as? WorkoutNotificationState.RestFinished
            if (!canPostNotifications() || restFinishedState == null) {
                showCurrentState()
                return@launch
            }
            val hasConnectedWearCompanion = runCatching {
                Wearable.getNodeClient(appContext).connectedNodes.await().isNotEmpty()
            }.getOrDefault(false)

            val notification = NotificationCompat.Builder(appContext, REST_TIMER_ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_rest_timer)
                .setContentTitle(restFinishedState.title)
                .setContentText(restFinishedState.text)
                .setContentIntent(contentIntent())
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setLocalOnly(hasConnectedWearCompanion)
                .setOnlyAlertOnce(false)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setVibrate(longArrayOf(0, 250, 160, 250))
                .apply {
                    restFinishedState.setAction?.let { addCompleteAction(it) }
                }
                .build()

            notificationManager.notify(REST_TIMER_NOTIFICATION_ID, notification)
            showCurrentState()
        }
    }

    private fun buildNotification(
        state: WorkoutNotificationState,
    ): android.app.Notification {
        val builder = NotificationCompat.Builder(appContext, WORKOUT_CONTROLS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rest_timer)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        when (state) {
            is WorkoutNotificationState.SetAction -> {
                builder
                    .setContentTitle(state.title)
                    .setContentText(state.text)
                    .addCompleteAction(state)
            }
            is WorkoutNotificationState.Resting -> {
                builder
                    .setContentTitle(state.title)
                    .setContentText(state.text)
                    .setWhen(state.restEndsAt)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .addAction(restAction("+30 sec", WorkoutNotificationActionReceiver.ACTION_ADD_REST))
                    .addAction(restAction("Skip rest", WorkoutNotificationActionReceiver.ACTION_SKIP_REST))
            }
            is WorkoutNotificationState.RestFinished -> {
                builder
                    .setContentTitle(state.title)
                    .setContentText(state.text)
                state.setAction?.let { builder.addCompleteAction(it) }
            }
            is WorkoutNotificationState.WaitingToFinish -> {
                builder
                    .setContentTitle(state.title)
                    .setContentText(state.text)
            }
        }

        return builder.build()
    }

    private fun NotificationCompat.Builder.addCompleteAction(
        action: WorkoutNotificationState.SetAction,
    ): NotificationCompat.Builder =
        addAction(
            R.drawable.ic_stat_rest_timer,
            "Complete set",
            WorkoutNotificationActionReceiver.pendingIntent(
                context = appContext,
                action = WorkoutNotificationActionReceiver.ACTION_COMPLETE_SET,
                requestCode = action.setId.toInt(),
                sessionId = action.sessionId,
                setId = action.setId,
            ),
        )

    private fun restAction(
        label: String,
        action: String,
    ): NotificationCompat.Action =
        NotificationCompat.Action(
            R.drawable.ic_stat_rest_timer,
            label,
            WorkoutNotificationActionReceiver.pendingIntent(
                context = appContext,
                action = action,
                requestCode = action.hashCode(),
            ),
        )

    private fun contentIntent(): PendingIntent =
        PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val WORKOUT_NOTIFICATION_ID = 1002
        const val REST_TIMER_NOTIFICATION_ID = 1001
    }
}
