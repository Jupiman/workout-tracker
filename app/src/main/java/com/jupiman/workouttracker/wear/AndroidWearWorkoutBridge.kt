package com.jupiman.workouttracker.wear

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.jupiman.workouttracker.data.local.dao.WorkoutSessionDao
import com.jupiman.workouttracker.data.repository.WorkoutSessionRepository
import com.jupiman.workouttracker.wearprotocol.CommandAck
import com.jupiman.workouttracker.wearprotocol.WorkoutWearCodecs
import com.jupiman.workouttracker.wearprotocol.WorkoutWearPaths
import com.jupiman.workouttracker.wearprotocol.WorkoutWearState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidWearWorkoutBridge(
    context: Context,
    private val workoutSessionRepository: WorkoutSessionRepository,
    private val workoutSessionDao: WorkoutSessionDao,
) {
    private val appContext = context.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val durationCommandResults = ConcurrentHashMap<String, Boolean>()
    @Volatile private var latestPublishedStateVersion: Long = 0L

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            workoutSessionRepository.activeSessionWithDetails.collectLatest { activeWorkout ->
                publishState(WorkoutWearStateProjector.stateFor(activeWorkout))
            }
        }
    }

    fun refresh() {
        scope.launch {
            publishCurrentState()
        }
    }

    suspend fun publishCurrentState(): WorkoutWearState {
        val state = WorkoutWearStateProjector.stateFor(workoutSessionDao.getActiveWithDetails())
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
        messageClient.sendMessage(
            sourceNodeId,
            WorkoutWearPaths.COMMAND_ACK,
            WorkoutWearCodecs.encodeCommandAck(ack),
        ).await()
    }

    private suspend fun publishState(state: WorkoutWearState) {
        val request = PutDataRequest.create(WorkoutWearPaths.STATE)
            .setData(WorkoutWearCodecs.encodeState(state))
            .setUrgent()
        dataClient.putDataItem(request).await()
        latestPublishedStateVersion = state.stateVersion
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
        messageClient.sendMessage(sourceNodeId, WorkoutWearPaths.COMMAND_ACK, WorkoutWearCodecs.encodeCommandAck(ack)).await()
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
        messageClient.sendMessage(
            sourceNodeId,
            WorkoutWearPaths.COMMAND_ACK,
            WorkoutWearCodecs.encodeCommandAck(ack),
        ).await()
    }
}

suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { throwable -> continuation.resumeWithException(throwable) }
        addOnCanceledListener { continuation.cancel() }
    }
