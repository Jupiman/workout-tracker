package com.jupiman.workouttracker.wear

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.jupiman.workouttracker.wearprotocol.CommandAck
import com.jupiman.workouttracker.wearprotocol.CompleteSetCommand
import com.jupiman.workouttracker.wearprotocol.FinishWorkoutCommand
import com.jupiman.workouttracker.wearprotocol.DurationSetCommand
import com.jupiman.workouttracker.wearprotocol.WearTrackingMode
import com.jupiman.workouttracker.wearprotocol.WearSessionStatus
import com.jupiman.workouttracker.wearprotocol.WorkoutWearCodecs
import com.jupiman.workouttracker.wearprotocol.WorkoutWearPaths
import com.jupiman.workouttracker.wearprotocol.WorkoutWearState
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

data class WearWorkoutUiState(
    val workoutState: WorkoutWearState? = null,
    val connected: Boolean = false,
    val pendingCommandId: String? = null,
    val pendingSetId: Long? = null,
    val now: Long = System.currentTimeMillis(),
    val phoneClockOffsetMillis: Long = 0L,
    val transientMessage: String? = null,
) {
    val phoneNow: Long
        get() = now + phoneClockOffsetMillis

    val canComplete: Boolean
        get() = connected &&
            pendingCommandId == null &&
            workoutState?.hasActionableSet == true &&
            workoutState.trackingMode != WearTrackingMode.DURATION

    val canDurationAction: Boolean
        get() = connected && pendingCommandId == null &&
            workoutState?.hasActionableSet == true && workoutState.trackingMode == WearTrackingMode.DURATION

    val canFinish: Boolean
        get() = connected && pendingCommandId == null && workoutState?.sessionId != null &&
            workoutState.sessionStatus == WearSessionStatus.WORKOUT_COMPLETE

    val displayState: WorkoutWearState?
        get() = workoutState
}

class WearWorkoutViewModel(
    application: Application,
) : AndroidViewModel(application),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener {

    private val appContext = application.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)
    private val vibrator = appContext.vibrator()
    private var alertedRestEndsAt: Long? = null
    private var alertedDurationStartsAt: Long? = null
    private var alertedDurationEndsAt: Long? = null

    private val _uiState = MutableStateFlow(WearWorkoutUiState())
    val uiState: StateFlow<WearWorkoutUiState> = _uiState

    init {
        dataClient.addListener(this)
        messageClient.addListener(this)
        viewModelScope.launch {
            loadLatestState()
            refreshConnectivityAndRequestState()
        }
        viewModelScope.launch {
            while (true) {
                tick()
                delay(1_000L)
            }
        }
        viewModelScope.launch {
            while (true) {
                refreshConnectivityAndRequestState()
                delay(5_000L)
            }
        }
    }

    override fun onCleared() {
        dataClient.removeListener(this)
        messageClient.removeListener(this)
        super.onCleared()
    }

    fun completeCurrentSet() {
        val snapshot = _uiState.value
        val state = snapshot.workoutState ?: return
        val sessionId = state.sessionId ?: return
        val setId = state.currentSetId ?: return
        if (!snapshot.canComplete) return

        val commandId = UUID.randomUUID().toString()
        _uiState.update {
            it.copy(
                pendingCommandId = commandId,
                pendingSetId = setId,
                transientMessage = null,
            )
        }
        vibrateClick()
        viewModelScope.launch {
            val nodes = connectedNodes()
            if (nodes.isEmpty()) {
                _uiState.update {
                    it.copy(
                        connected = false,
                        pendingCommandId = null,
                        pendingSetId = null,
                        transientMessage = "Phone disconnected",
                    )
                }
                return@launch
            }
            val command = CompleteSetCommand(
                sessionId = sessionId,
                sessionSetId = setId,
                commandId = commandId,
                observedStateVersion = state.stateVersion,
                createdAt = System.currentTimeMillis(),
            )
            val payload = WorkoutWearCodecs.encodeCompleteSetCommand(command)
            runCatching {
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, WorkoutWearPaths.COMPLETE_SET, payload).await()
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        pendingCommandId = null,
                        pendingSetId = null,
                        transientMessage = throwable.message ?: "Could not reach phone",
                    )
                }
            }
        }
    }

    fun startDurationSet() = sendDurationCommand(WorkoutWearPaths.START_DURATION_SET)
    fun stopDurationSet() = sendDurationCommand(WorkoutWearPaths.STOP_DURATION_SET)
    fun cancelDurationSet() = sendDurationCommand(WorkoutWearPaths.CANCEL_DURATION_SET)

    private fun sendDurationCommand(path: String) {
        val snapshot = _uiState.value
        val state = snapshot.workoutState ?: return
        val sessionId = state.sessionId ?: return
        val setId = state.currentSetId ?: return
        if (!snapshot.canDurationAction) return
        val commandId = UUID.randomUUID().toString()
        _uiState.update { it.copy(pendingCommandId = commandId, pendingSetId = setId, transientMessage = null) }
        vibrateClick()
        viewModelScope.launch {
            runCatching {
                val nodes = connectedNodes()
                require(nodes.isNotEmpty()) { "Phone disconnected" }
                val command = DurationSetCommand(
                    sessionId = sessionId,
                    sessionSetId = setId,
                    commandId = commandId,
                    observedStateVersion = state.stateVersion,
                    createdAt = System.currentTimeMillis(),
                )
                val payload = WorkoutWearCodecs.encodeDurationSetCommand(command)
                nodes.forEach { node -> messageClient.sendMessage(node.id, path, payload).await() }
            }.onFailure { failure ->
                _uiState.update {
                    if (it.pendingCommandId == commandId) it.copy(
                        pendingCommandId = null,
                        pendingSetId = null,
                        transientMessage = failure.message ?: "Could not reach phone",
                    ) else it
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            val item = event.dataItem
            if (event.type == DataEvent.TYPE_CHANGED && item.uri.path == WorkoutWearPaths.STATE) {
                val data = item.data ?: return@forEach
                val state = runCatching { WorkoutWearCodecs.decodeState(data) }.getOrNull()
                if (state != null) {
                    acceptState(state)
                }
            }
        }
    }

    fun finishWorkout() {
        val snapshot = _uiState.value
        if (!snapshot.canFinish) return
        val sessionId = snapshot.workoutState?.sessionId ?: return
        val commandId = UUID.randomUUID().toString()
        _uiState.update { it.copy(pendingCommandId = commandId, pendingSetId = null, transientMessage = null) }
        vibrateClick()
        viewModelScope.launch {
            delay(10_000L)
            _uiState.update {
                if (it.pendingCommandId == commandId) it.copy(
                    pendingCommandId = null,
                    transientMessage = "Phone did not respond. Try again.",
                ) else it
            }
        }
        viewModelScope.launch {
            runCatching {
                val nodes = connectedNodes()
                require(nodes.isNotEmpty()) { "Phone disconnected" }
                val payload = WorkoutWearCodecs.encodeFinishWorkoutCommand(FinishWorkoutCommand(sessionId, commandId))
                nodes.forEach { node ->
                    messageClient.sendMessage(node.id, WorkoutWearPaths.FINISH_WORKOUT, payload).await()
                }
            }.onFailure { failure ->
                _uiState.update {
                    if (it.pendingCommandId == commandId) it.copy(
                        pendingCommandId = null,
                        transientMessage = failure.message ?: "Could not reach phone",
                    ) else it
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WorkoutWearPaths.COMMAND_ACK) return
        val ack = runCatching { WorkoutWearCodecs.decodeCommandAck(messageEvent.data) }.getOrNull()
        if (ack != null) acceptAck(ack)
    }

    private fun acceptState(state: WorkoutWearState) {
        val receivedAt = System.currentTimeMillis()
        _uiState.update { current ->
            val pendingSetChanged = (current.pendingSetId != null &&
                current.pendingSetId != state.currentSetId) ||
                current.workoutState?.sessionId != state.sessionId
            current.copy(
                workoutState = state,
                now = receivedAt,
                phoneClockOffsetMillis = state.updatedAt - receivedAt,
                pendingCommandId = if (pendingSetChanged) null else current.pendingCommandId,
                pendingSetId = if (pendingSetChanged) null else current.pendingSetId,
                transientMessage = if (current.workoutState?.sessionStatus != state.sessionStatus) null else current.transientMessage,
            )
        }
        if (state.restEndsAt != alertedRestEndsAt && state.restEndsAt?.let { it > state.updatedAt } == true) {
            alertedRestEndsAt = null
        }
        if (state.durationStartsAt != alertedDurationStartsAt && state.durationStartsAt?.let { it > state.updatedAt } == true) {
            alertedDurationStartsAt = null
        }
        if (state.durationEndsAt != alertedDurationEndsAt && state.durationEndsAt?.let { it > state.updatedAt } == true) {
            alertedDurationEndsAt = null
        }
    }

    private fun acceptAck(ack: CommandAck) {
        _uiState.update { current ->
            if (current.pendingCommandId == ack.commandId) {
                current.copy(
                    pendingCommandId = null,
                    pendingSetId = null,
                    transientMessage = if (ack.accepted) null else ack.message,
                )
            } else {
                current
            }
        }
    }

    private suspend fun loadLatestState() {
        runCatching {
            val buffer = dataClient.dataItems.await()
            try {
                buffer
                    .asSequence()
                    .firstOrNull { it.uri.path == WorkoutWearPaths.STATE }
                    ?.data
                    ?.let(WorkoutWearCodecs::decodeState)
            } finally {
                buffer.release()
            }
        }.getOrNull()?.let(::acceptState)
    }

    private suspend fun refreshConnectivityAndRequestState() {
        val nodes = connectedNodes()
        _uiState.update { it.copy(connected = nodes.isNotEmpty()) }
        nodes.forEach { node ->
            runCatching {
                messageClient.sendMessage(node.id, WorkoutWearPaths.REQUEST_STATE, ByteArray(0)).await()
            }
        }
    }

    private suspend fun connectedNodes() =
        runCatching { nodeClient.connectedNodes.await() }.getOrDefault(emptyList())

    private fun tick() {
        val now = System.currentTimeMillis()
        _uiState.update { it.copy(now = now) }
        val snapshot = _uiState.value
        val state = snapshot.workoutState ?: return
        val restEndsAt = state.restEndsAt
        if (restEndsAt != null && snapshot.phoneNow >= restEndsAt && alertedRestEndsAt != restEndsAt) {
            alertedRestEndsAt = restEndsAt
            vibrateReady()
        }
        val startsAt = state.durationStartsAt
        if (startsAt != null && snapshot.phoneNow >= startsAt && alertedDurationStartsAt != startsAt) {
            alertedDurationStartsAt = startsAt
            vibrateReady()
        }
        val endsAt = state.durationEndsAt
        if (endsAt != null && snapshot.phoneNow >= endsAt && alertedDurationEndsAt != endsAt) {
            alertedDurationEndsAt = endsAt
            vibrateReady()
        }
    }

    private fun vibrateClick() {
        vibrate(35)
    }

    private fun vibrateReady() {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 180L, 90L, 180L),
                    intArrayOf(0, 255, 0, 255),
                    -1,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0L, 180L, 90L, 180L), -1)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(durationMillis: Long) {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(durationMillis)
        }
    }
}

private fun Context.vibrator(): Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java)
    }

suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { throwable -> continuation.resumeWithException(throwable) }
        addOnCanceledListener { continuation.cancel() }
    }
