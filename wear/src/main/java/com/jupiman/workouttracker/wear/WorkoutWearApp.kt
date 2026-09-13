package com.jupiman.workouttracker.wear

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.jupiman.workouttracker.wearprotocol.WearSessionStatus
import com.jupiman.workouttracker.wearprotocol.WorkoutWearState

@Composable
fun WorkoutWearApp(
    viewModel: WearWorkoutViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    MaterialTheme {
        Scaffold(
            timeText = { TimeText() },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = uiState.displayState?.sessionStatus ?: WearSessionStatus.NO_ACTIVE,
                    label = "wear-state",
                ) { status ->
                    when (status) {
                        WearSessionStatus.ACTIVE -> ActiveWorkoutScreen(
                            state = uiState.displayState,
                            connected = uiState.connected,
                            pending = uiState.pendingCommandId != null,
                            now = uiState.phoneNow,
                            canComplete = uiState.canComplete,
                            onComplete = viewModel::completeCurrentSet,
                        )
                        WearSessionStatus.WORKOUT_COMPLETE -> SimpleStateScreen(
                            title = "Workout complete",
                            body = null,
                            connected = uiState.connected,
                        )
                        WearSessionStatus.NO_ACTIVE,
                        WearSessionStatus.UNAVAILABLE -> SimpleStateScreen(
                            title = "Workout Tracker",
                            body = "No active workout\nStart a workout on your phone.",
                            connected = uiState.connected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveWorkoutScreen(
    state: WorkoutWearState?,
    connected: Boolean,
    pending: Boolean,
    now: Long,
    canComplete: Boolean,
    onComplete: () -> Unit,
) {
    if (state == null) return
    val restText = restText(state.restEndsAt, now)
    val targetText = "${formatCentiKg(state.weightCentiKg ?: 0)} kg x ${state.targetReps ?: 0}"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatusText(
            connected = connected,
            supersetPosition = state.supersetPosition,
            supersetSize = state.supersetSize,
        )
        Text(
            text = state.exerciseName.orEmpty().uppercase(),
            textAlign = TextAlign.Center,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = targetText,
            textAlign = TextAlign.Center,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = state.setLabel.orEmpty().uppercase(),
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.78f),
            maxLines = 1,
        )
        if (restText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = restText,
                textAlign = TextAlign.Center,
                fontSize = if (restText == "READY") 16.sp else 14.sp,
                fontWeight = if (restText == "READY") FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colors.primary,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onComplete,
            enabled = canComplete,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .height(52.dp),
            colors = ButtonDefaults.primaryButtonColors(),
        ) {
            if (pending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = "COMPLETE SET",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StatusText(
    connected: Boolean,
    supersetPosition: Int?,
    supersetSize: Int?,
) {
    val text = when {
        !connected -> "Phone disconnected"
        supersetPosition != null && supersetSize != null -> "Superset • $supersetPosition/$supersetSize"
        else -> null
    }
    Text(
        text = text.orEmpty(),
        textAlign = TextAlign.Center,
        fontSize = 11.sp,
        color = MaterialTheme.colors.onBackground.copy(alpha = 0.58f),
        minLines = 1,
        maxLines = 1,
    )
    Spacer(modifier = Modifier.height(2.dp))
}

@Composable
private fun SimpleStateScreen(
    title: String,
    body: String?,
    connected: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            textAlign = TextAlign.Center,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (body != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.78f),
            )
        }
        if (!connected) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Phone disconnected",
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.58f),
            )
        }
    }
}
