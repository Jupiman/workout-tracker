package com.jupiman.workouttracker.selfhosted

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.jupiman.workouttracker.WorkoutTrackerApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.jupiman.workouttracker.preferences.AppPreferencesRepository

class AndroidSelfHostedSyncScheduler(
    context: Context,
    private val scope: CoroutineScope,
    private val preferencesRepository: AppPreferencesRepository,
    private val tokenStore: SelfHostedTokenStore,
    private val transportPolicy: SelfHostedTransportPolicy = SelfHostedTransportPolicy.HTTP_ALLOWED,
) : SelfHostedWorkoutScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedulePending() {
        scope.launch {
            val preferences = preferencesRepository.current()
            if (preferences.selfHostedSyncEnabled && tokenStore.hasToken() &&
                validateServerAddress(
                    preferences.selfHostedServerAddress,
                    preferences.selfHostedUseHttps,
                    transportPolicy,
                ) is ServerAddressValidation.Valid
            ) {
                enqueue(full = false, ExistingWorkPolicy.KEEP)
            }
        }
    }

    override fun scheduleFullSync() = enqueue(full = true, ExistingWorkPolicy.REPLACE)

    override fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    private fun enqueue(full: Boolean, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<SelfHostedSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(Data.Builder().putBoolean(SelfHostedSyncWorker.FULL_SYNC, full).build())
            .build()
        workManager.enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "self-hosted-workout-sync"
    }
}

class SelfHostedSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val manager = (applicationContext as WorkoutTrackerApplication).container.selfHostedSyncManager
        val summary = if (inputData.getBoolean(FULL_SYNC, false)) manager.syncAllIfEnabled() else manager.syncPending()
        return if (summary.retryNeeded) Result.retry() else Result.success()
    }

    companion object {
        const val FULL_SYNC = "full_sync"
    }
}
