package com.jupiman.workouttracker

import android.app.Application
import com.jupiman.workouttracker.di.AppContainer
import com.jupiman.workouttracker.notification.createNotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WorkoutTrackerApplication : Application() {
    lateinit var container: AppContainer
        private set
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
        container = AppContainer(this)
        applicationScope.launch {
            container.workoutSessionRepository.syncRestTimerAlarm()
            container.workoutNotificationCoordinator.showCurrentState()
        }
    }
}
