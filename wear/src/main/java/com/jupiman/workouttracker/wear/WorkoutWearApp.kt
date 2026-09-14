package com.jupiman.workouttracker.wear

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Colors
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
    val view = LocalView.current
    val keepScreenOn = uiState.displayState?.sessionStatus == WearSessionStatus.ACTIVE

    DisposableEffect(view, keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose {
            view.keepScreenOn = false
        }
    }

    MaterialTheme(
        colors = Colors(
            primary = WearLavender,
            onPrimary = Color(0xFF24124F),
            background = WearInk,
            onBackground = Color(0xFFEDE8F8),
            surface = Color(0xFF1B1C25),
            onSurface = Color(0xFFEDE8F8),
        ),
    ) {
        Scaffold(
            timeText = { TimeText() },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colors.background)
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
                            transientMessage = uiState.transientMessage,
                            canComplete = uiState.canComplete,
                            onComplete = viewModel::completeCurrentSet,
                        )
                        WearSessionStatus.WORKOUT_COMPLETE -> SimpleStateScreen(
                            title = "Workout complete",
                            body = null,
                            connected = uiState.connected,
                            transientMessage = uiState.transientMessage,
                            action = {
                                Button(
                                    onClick = viewModel::finishWorkout,
                                    enabled = uiState.canFinish,
                                    modifier = Modifier.fillMaxWidth(0.9f).height(48.dp),
                                    shape = RoundedCornerShape(24.dp),
                                ) {
                                    Text(
                                        text = if (uiState.pendingCommandId != null) "Finishing…" else "Finish workout",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            },
                        )
                        WearSessionStatus.NO_ACTIVE,
                        WearSessionStatus.UNAVAILABLE -> SimpleStateScreen(
                            title = "Workout Companion",
                            body = "No active workout\nStart a workout on your phone.",
                            connected = uiState.connected,
                            transientMessage = uiState.transientMessage,
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
    transientMessage: String?,
    canComplete: Boolean,
    onComplete: () -> Unit,
) {
    if (state == null) return
    val restText = restText(state.restEndsAt, now)
    val restOverdue = isRestOverdue(state.restEndsAt, now)
    val targetText = "${formatCentiKg(state.weightCentiKg ?: 0)} kg x ${state.targetReps ?: 0}"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatusText(
            connected = connected,
            pending = pending,
            supersetPosition = state.supersetPosition,
            supersetSize = state.supersetSize,
        )
        Text(
            text = state.exerciseName.orEmpty().uppercase(),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(5.dp))
        TargetPanel(
            targetText = targetText,
            setLabel = state.setLabel.orEmpty().uppercase(),
        )
        if (restText != null) {
            Spacer(modifier = Modifier.height(6.dp))
            StatusPill(
                text = restText,
                container = if (restOverdue) WearReady.copy(alpha = 0.18f) else WearLavender.copy(alpha = 0.14f),
                content = if (restOverdue) WearReady else MaterialTheme.colors.primary,
            )
        }
        if (transientMessage != null) {
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = transientMessage,
                textAlign = TextAlign.Center,
                fontSize = 10.sp,
                color = WearWarning,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = onComplete,
            enabled = canComplete,
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = MaterialTheme.colors.onPrimary,
                disabledBackgroundColor = MaterialTheme.colors.surface,
                disabledContentColor = MaterialTheme.colors.onSurface.copy(alpha = 0.45f),
            ),
        ) {
            if (pending) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = "SENT",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    text = "COMPLETE SET",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

private val WearInk = Color(0xFF101116)
private val WearLavender = Color(0xFFC9B6FF)
private val WearReady = Color(0xFF8AD6A4)
private val WearSurface = Color(0xFF1B1C25)
private val WearWarning = Color(0xFFFFD166)

@Composable
private fun TargetPanel(
    targetText: String,
    setLabel: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .background(
                color = WearSurface,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = targetText,
            textAlign = TextAlign.Center,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = setLabel,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.76f),
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    container: Color,
    content: Color,
) {
    Row(
        modifier = Modifier
            .background(
                color = container,
                shape = RoundedCornerShape(percent = 50),
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatusText(
    connected: Boolean,
    pending: Boolean,
    supersetPosition: Int?,
    supersetSize: Int?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusPill(
            text = if (connected) "Phone" else "Disconnected",
            container = if (connected) WearReady.copy(alpha = 0.14f) else WearWarning.copy(alpha = 0.16f),
            content = if (connected) WearReady else WearWarning,
        )
        if (pending) {
            StatusPill(
                text = "Syncing",
                container = WearLavender.copy(alpha = 0.14f),
                content = MaterialTheme.colors.primary,
            )
        }
        if (supersetPosition != null && supersetSize != null) {
            StatusPill(
                text = "$supersetPosition/$supersetSize",
                container = WearSurface,
                content = MaterialTheme.colors.onSurface.copy(alpha = 0.84f),
            )
        }
    }
    Spacer(modifier = Modifier.height(5.dp))
}

@Composable
private fun SimpleStateScreen(
    title: String,
    body: String?,
    connected: Boolean,
    transientMessage: String?,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StatusPill(
            text = if (connected) "Phone connected" else "Phone disconnected",
            container = if (connected) WearReady.copy(alpha = 0.14f) else WearWarning.copy(alpha = 0.16f),
            content = if (connected) WearReady else WearWarning,
        )
        Spacer(modifier = Modifier.height(8.dp))
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
        if (action != null) {
            Spacer(modifier = Modifier.height(10.dp))
            action()
        }
        if (transientMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = transientMessage,
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = WearWarning,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
