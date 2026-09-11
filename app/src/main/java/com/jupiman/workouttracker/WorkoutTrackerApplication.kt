package com.jupiman.workouttracker

import android.app.Application
import com.jupiman.workouttracker.di.AppContainer

class WorkoutTrackerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

