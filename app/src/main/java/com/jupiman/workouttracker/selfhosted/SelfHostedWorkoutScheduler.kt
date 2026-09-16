package com.jupiman.workouttracker.selfhosted

interface SelfHostedWorkoutScheduler {
    fun schedulePending()
    fun scheduleFullSync()
    fun cancel()
}

object NoOpSelfHostedWorkoutScheduler : SelfHostedWorkoutScheduler {
    override fun schedulePending() = Unit
    override fun scheduleFullSync() = Unit
    override fun cancel() = Unit
}
