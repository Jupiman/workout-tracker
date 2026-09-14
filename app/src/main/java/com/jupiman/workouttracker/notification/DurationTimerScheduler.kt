package com.jupiman.workouttracker.notification

interface DurationTimerScheduler {
    fun schedule(durationEndsAt: Long)
    fun cancel()
}

object NoOpDurationTimerScheduler : DurationTimerScheduler {
    override fun schedule(durationEndsAt: Long) = Unit
    override fun cancel() = Unit
}
