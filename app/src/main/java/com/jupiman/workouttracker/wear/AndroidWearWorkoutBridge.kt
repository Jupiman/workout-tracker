package com.jupiman.workouttracker.wear

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.jupiman.workouttracker.data.local.dao.WorkoutSessionDao
import com.jupiman.workouttracker.data.repository.WorkoutSessionRepository
import com.jupiman.workouttracker.preferences.AppPreferencesRepository
import com.jupiman.workouttracker.preferences.WeightUnit
import com.jupiman.workouttracker.wearprotocol.CommandAck
import com.jupiman.workouttracker.wearprotocol.WorkoutWearCodecs
import com.jupiman.workouttracker.wearprotocol.WorkoutWearPaths
import com.jupiman.workouttracker.wearprotocol.WorkoutWearState
import com.jupiman.workouttracker.wearprotocol.WearWeightUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidWearWorkoutBridge(
    context: Context,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val workoutSessionDao: WorkoutSessionDao,
    private val appPreferencesRepository: AppPreferencesRepository,
) {
    private val appContext = context.applicationContext
    private val started = AtomicBoolean(false)
    private val lastFailureLogAt = AtomicLong(0L)
    private val dataClient by lazy { Wearable.getDataClient(appContext) }
    private val messageClient by lazy { Wearable.getMessageClient(appContext) }
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            logWearFailure("background operation", throwable)
        },
    )
    private val durationCommandResults = ConcurrentHashMap<String, Boolean>()
    @Volatile private var latestPublishedStateVersion: Long = 0L

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(
                workoutSessionRepository.activeSessionWithDetails,
                appPreferencesRepository.preferences,
            ) { activeWorkout, preferences -> activeWorkout to preferences.weightUnit }
                .collectLatest { (activeWorkout, weightUnit) ->
                publishState(WorkoutWearStateProjector.stateFor(activeWorkout, weightUnit = weightUnit.toWearUnit()))
            }
        }
    }

    fun refresh() {
        scope.launch {
            publishCurrentState()
        }
    }

    suspend fun publishCurrentState(): WorkoutWearState {
        val state = WorkoutWearStateProjector.stateFor(
            workoutSessionDao.getActiveWithDetails(),
            weightUnit = appPreferencesRepository.current().weightUnit.toWearUnit(),
        )
        publishState(state)
        return state
    }

    suspend fun handleCompleteSetCommand(
        bytes: ByteArray,
        sourceNodeId: String,
    ) {
        val command = runCatching { WorkoutWearCodecs.decodeCompleteSetCommand(bytes) }
            .getOrElse { return }
        val accepted = workoutSessionRepository.completeSetFromWearCommand(
            expectedSessionId = command.sessionId,
            setId = command.sessionSetId,
        )
        val state = publishCurrentState()
        val ack = CommandAck(
            commandId = command.commandId,
            accepted = accepted,
            stateVersion = state.stateVersion,
            message = if (accepted) null else "Command ignored because the set is no longer current.",
        )
        sendMessage(
            sourceNodeId,
            WorkoutWearPaths.COMMAND_ACK,
            WorkoutWearCodecs.encodeCommandAck(ack),
        )
    }

    private suspend fun publishState(state: WorkoutWearState) {
        val request = PutDataRequest.create(WorkoutWearPaths.STATE)
            .setData(WorkoutWearCodecs.encodeState(state))
            .setUrgent()
        val published = runOptionalWearOperation(
            onFailure = { logWearFailure("publish state", it) },
        ) {
            dataClient.putDataItem(request).await()
        }
        if (published) latestPublishedStateVersion = state.stateVersion
    }

    suspend fun handleFinishWorkoutCommand(bytes: ByteArray, sourceNodeId: String) {
        val command = runCatching { WorkoutWearCodecs.decodeFinishWorkoutCommand(bytes) }.getOrNull() ?: return
        val result = runCatching {
            workoutSessionRepository.finishActiveWorkout(
                allowPartial = false,
                expectedWearSessionId = command.sessionId,
            )
        }
        val state = publishCurrentState()
        val ack = CommandAck(
            commandId = command.commandId,
            accepted = result.isSuccess,
            stateVersion = state.stateVersion,
            message = result.exceptionOrNull()?.message,
        )
        sendMessage(sourceNodeId, WorkoutWearPaths.COMMAND_ACK, WorkoutWearCodecs.encodeCommandAck(ack))
    }

    suspend fun handleDurationSetCommand(path: String, bytes: ByteArray, sourceNodeId: String) {
        val command = runCatching { WorkoutWearCodecs.decodeDurationSetCommand(bytes) }.getOrNull() ?: return
        val accepted = durationCommandResults[command.commandId] ?: run {
            val currentVersion = latestPublishedStateVersion
            val stateIsFresh = currentVersion == 0L || command.observedStateVersion == currentVersion
            val result = if (!stateIsFresh) false else when (path) {
                WorkoutWearPaths.START_DURATION_SET -> workoutSessionRepository.startDurationSet(
                    expectedSessionId = command.sessionId,
                    setId = command.sessionSetId,
                )
                WorkoutWearPaths.STOP_DURATION_SET -> workoutSessionRepository.stopDurationSet(
                    expectedSessionId = command.sessionId,
                    setId = command.sessionSetId,
                )
                WorkoutWearPaths.CANCEL_DURATION_SET -> workoutSessionRepository.cancelDurationSet(
                    expectedSessionId = command.sessionId,
                    setId = command.sessionSetId,
                )
                else -> false
            }
            if (durationCommandResults.size >= 128) durationCommandResults.clear()
            durationCommandResults[command.commandId] = result
            result
        }
        val state = publishCurrentState()
        val ack = CommandAck(
            commandId = command.commandId,
            accepted = accepted,
            stateVersion = state.stateVersion,
            message = if (accepted) null else "Command ignored because the duration set changed.",
        )
        sendMessage(
            sourceNodeId,
            WorkoutWearPaths.COMMAND_ACK,
            WorkoutWearCodecs.encodeCommandAck(ack),
        )
    }

    private suspend fun sendMessage(nodeId: String, path: String, data: ByteArray) {
        runOptionalWearOperation(
            onFailure = { logWearFailure("send message", it) },
        ) {
            messageClient.sendMessage(nodeId, path, data).await()
        }
    }

    private fun logWearFailure(operation: String, throwable: Throwable) {
        val now = SystemClock.elapsedRealtime()
        val previous = lastFailureLogAt.get()
        if ((previous == 0L || now - previous >= FAILURE_LOG_INTERVAL_MILLIS) &&
            lastFailureLogAt.compareAndSet(previous, now)
        ) {
            Log.w(TAG, "Wear $operation failed; Wear support is temporarily unavailable.", throwable)
        }
    }

    private companion object {
        const val TAG = "WearWorkoutBridge"
        const val FAILURE_LOG_INTERVAL_MILLIS = 60_000L
    }
}

internal suspend fun runOptionalWearOperation(
    onFailure: (Throwable) -> Unit,
    operation: suspend () -> Unit,
): Boolean = try {
    operation()
    true
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Exception) {
    onFailure(failure)
    false
}

private fun WeightUnit.toWearUnit(): WearWeightUnit = when (this) {
    WeightUnit.KG -> WearWeightUnit.KG
    WeightUnit.LB -> WearWeightUnit.LB
}

suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { throwable -> continuation.resumeWithException(throwable) }
        addOnCanceledListener { continuation.cancel() }
    }
