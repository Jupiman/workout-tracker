package com.jupiman.workouttracker

import android.app.Application
import com.jupiman.workouttracker.di.AppContainer
import com.jupiman.workouttracker.notification.createNotificationChannels

class WorkoutTrackerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
        container = AppContainer(this)
    }
}
