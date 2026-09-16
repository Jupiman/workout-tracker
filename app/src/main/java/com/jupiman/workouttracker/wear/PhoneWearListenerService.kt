package com.jupiman.workouttracker.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.jupiman.workouttracker.WorkoutTrackerApplication
import com.jupiman.workouttracker.wearprotocol.WorkoutWearPaths
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PhoneWearListenerService : WearableListenerService() {
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, throwable ->
            Log.w(TAG, "Wear command handling failed.", throwable)
        },
    )

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val bridge = (applicationContext as WorkoutTrackerApplication).container.wearWorkoutBridge
        when (messageEvent.path) {
            WorkoutWearPaths.FINISH_WORKOUT -> {
                scope.launch {
                    bridge.handleFinishWorkoutCommand(messageEvent.data, messageEvent.sourceNodeId)
                }
            }
            WorkoutWearPaths.REQUEST_STATE -> {
                scope.launch {
                    bridge.publishCurrentState()
                }
            }
            WorkoutWearPaths.COMPLETE_SET -> {
                scope.launch {
                    bridge.handleCompleteSetCommand(
                        bytes = messageEvent.data,
                        sourceNodeId = messageEvent.sourceNodeId,
                    )
                }
            }
            WorkoutWearPaths.START_DURATION_SET,
            WorkoutWearPaths.STOP_DURATION_SET,
            WorkoutWearPaths.CANCEL_DURATION_SET -> {
                scope.launch {
                    bridge.handleDurationSetCommand(
                        path = messageEvent.path,
                        bytes = messageEvent.data,
                        sourceNodeId = messageEvent.sourceNodeId,
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "PhoneWearListener"
    }
}
