package com.jupiman.workouttracker.notification

interface WorkoutNotificationUpdater {
    fun refresh()
    fun cancel()
}

object NoOpWorkoutNotificationUpdater : WorkoutNotificationUpdater {
    override fun refresh() = Unit
    override fun cancel() = Unit
}
