package com.jupiman.workouttracker.notification

interface RestTimerScheduler {
    fun schedule(restEndsAt: Long)
    fun cancel()
}

