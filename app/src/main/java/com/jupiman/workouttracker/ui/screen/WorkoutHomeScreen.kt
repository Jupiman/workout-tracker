package com.jupiman.workouttracker.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.entity.TrackingMode
import com.jupiman.workouttracker.data.repository.trackingText
import com.jupiman.workouttracker.data.local.model.SessionExerciseWithSets
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import com.jupiman.workouttracker.data.repository.formatWeightValue
import com.jupiman.workouttracker.data.repository.ProgressionFinishChoice
import com.jupiman.workouttracker.notification.WorkoutNotificationProjector
import com.jupiman.workouttracker.preferences.WeightUnit
import com.jupiman.workouttracker.ui.LocalAppPreferences
import com.jupiman.workouttracker.ui.component.WeightAdjuster
import com.jupiman.workouttracker.ui.theme.StatusPill
import com.jupiman.workouttracker.ui.theme.WorkoutRadii
import com.jupiman.workouttracker.ui.theme.WorkoutSpacing
import com.jupiman.workouttracker.ui.theme.WorkoutVisualState
import com.jupiman.workouttracker.ui.theme.WorkoutEmptyState
import com.jupiman.workouttracker.ui.theme.WorkoutGlyph
import com.jupiman.workouttracker.ui.theme.WorkoutIcon
import com.jupiman.workouttracker.ui.theme.workoutStateColors
import com.jupiman.workouttracker.ui.viewmodel.HomeUiState
import com.jupiman.workouttracker.ui.viewmodel.HomeViewModel
import com.jupiman.workouttracker.ui.viewmodel.LastTimeExerciseContext
import com.jupiman.workouttracker.ui.viewmodel.LastTimeSetContext
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

@Composable
fun WorkoutHomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onCreateProgram: () -> Unit = {},
    onOpenProgram: () -> Unit = {},
) {
    val weightUnit = LocalAppPreferences.current.weightUnit
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val completionSummary by viewModel.completionSummary.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message, undo, uiState.activeWorkout?.session?.id, completionSummary) {
        if (completionSummary != null) return@LaunchedEffect
        val receipt = undo
        if (receipt != null && uiState.activeWorkout != null) {
            val result = snackbarHostState.showSnackbar(
                message = receipt.label,
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoCompletion(receipt)
            else viewModel.dismissUndo(receipt)
            return@LaunchedEffect
        }
        val currentMessage = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(currentMessage)
        viewModel.clearMessage()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            val activeWorkout = uiState.activeWorkout
            RestTimerBottomBar(
                restEndsAt = if (completionSummary == null) activeWorkout?.session?.restEndsAt else null,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = WorkoutSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.section),
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            val activeWorkout = uiState.activeWorkout
            val summary = completionSummary
            if (summary != null) {
                item {
                    WorkoutCompletionPanel(summary, onDone = { viewModel.dismissCompletionSummary(summary.sessionId) })
                }
            } else if (activeWorkout == null) {
                item {
                    when {
                        uiState.activeProgram == null -> WorkoutEmptyState(
                            title = "Create your first program",
                            body = "Create a program, add a training day, and choose exercises from the ready-to-use library.",
                            actionLabel = "Create program",
                            onAction = onCreateProgram,
                        )
                        uiState.activeProgramTemplates.isEmpty() -> WorkoutEmptyState(
                            title = "Add your first training day",
                            body = "Open your program and add a training day before starting a workout.",
                            actionLabel = "Open program",
                            onAction = onOpenProgram,
                        )
                        else -> StartWorkoutPanel(
                            uiState = uiState,
                            onStartWorkout = viewModel::startWorkout,
                        )
                    }
                }
            } else {
                item {
                    ActiveWorkoutPanel(
                        activeWorkout = activeWorkout,
                        exercises = uiState.exercises,
                        lastTimeByTemplateExerciseId = uiState.lastTimeByTemplateExerciseId,
                        onCompleteSet = { setId, weight, reps, mode ->
                            viewModel.completeSet(setId, weight, reps, mode, weightUnit)
                        },
                        onStartDurationSet = viewModel::startDurationSet,
                        onCancelDurationSet = viewModel::cancelDurationSet,
                        onStopDurationSet = viewModel::stopDurationSet,
                        onUncompleteSet = viewModel::uncompleteSet,
                        onSkipSet = viewModel::skipSet,
                        onDiscardWorkout = viewModel::discardActiveWorkout,
                        onFinishWorkout = viewModel::finishActiveWorkout,
                        onAddSessionSet = viewModel::addSessionSet,
                        onReplaceExerciseForToday = viewModel::replaceExerciseForToday,
                        onSkipExercise = viewModel::skipExercise,
                        onDoLater = viewModel::doExerciseLater,
                        onAddExercise = { sessionId, exerciseId, sets, reps, weight, rest, mode, duration ->
                            viewModel.addExerciseForToday(
                                sessionId, exerciseId, sets, reps, weight, rest, mode, duration, weightUnit,
                            )
                        },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StartWorkoutPanel(
    uiState: HomeUiState,
    onStartWorkout: (Long?) -> Unit,
) {
    val nextWorkoutAvailable = uiState.nextWorkoutTemplateId != null
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column(
                modifier = Modifier.padding(WorkoutSpacing.card),
                verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item),
            ) {
                StatusPill(
                    text = if (nextWorkoutAvailable) "Ready" else "Setup needed",
                    state = if (nextWorkoutAvailable) WorkoutVisualState.Ready else WorkoutVisualState.Disabled,
                )
                Text(
                    text = uiState.activeProgram?.name ?: "No active program yet",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = uiState.nextWorkoutName ?: "No workout templates yet",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { onStartWorkout(uiState.nextWorkoutTemplateId) },
                    enabled = nextWorkoutAvailable,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start workout")
                }
            }
        }

        if (uiState.activeProgramTemplates.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = "Choose another workout",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            uiState.activeProgramTemplates.forEach { template ->
                WorkoutChoiceButton(
                    template = template,
                    onStartWorkout = onStartWorkout,
                )
            }
        }
    }
}

@Composable
private fun WorkoutChoiceButton(
    template: WorkoutTemplateEntity,
    onStartWorkout: (Long?) -> Unit,
) {
    OutlinedButton(
        onClick = { onStartWorkout(template.id) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(template.name)
    }
}

@Composable
private fun ActiveWorkoutPanel(
    activeWorkout: WorkoutSessionWithDetails,
    exercises: List<ExerciseEntity>,
    lastTimeByTemplateExerciseId: Map<Long, LastTimeExerciseContext>,
    onCompleteSet: (Long, String, String, TrackingMode) -> Unit,
    onStartDurationSet: (Long, Long) -> Unit,
    onCancelDurationSet: (Long, Long) -> Unit,
    onStopDurationSet: (Long, Long) -> Unit,
    onUncompleteSet: (Long) -> Unit,
    onSkipSet: (Long) -> Unit,
    onDiscardWorkout: () -> Unit,
    onFinishWorkout: (Boolean, Map<Long, ProgressionFinishChoice>) -> Unit,
    onAddExercise: suspend (Long, Long, String, String, String, String, TrackingMode, String) -> Unit,
    onAddSessionSet: (Long, SetType) -> Unit,
    onReplaceExerciseForToday: (Long, Long) -> Unit,
    onSkipExercise: (Long) -> Unit,
    onDoLater: (Long) -> Unit,
) {
    val weightUnit = LocalAppPreferences.current.weightUnit
    var confirmingDiscard by remember { mutableStateOf(false) }
    var addingExercise by remember(activeWorkout.session.id) { mutableStateOf(false) }
    var confirmingPartialFinish by remember { mutableStateOf(false) }
    var showingProgressionReview by remember { mutableStateOf(false) }
    var pendingFinishAllowsPartial by remember { mutableStateOf(false) }
    val progressionReviewItems = activeWorkout.progressionReviewItems(weightUnit)
    val currentSetFocus = activeWorkout.currentSetFocus()
    var durationNow by remember(activeWorkout.session.id) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeWorkout.session.durationStartsAt, activeWorkout.session.durationEndsAt) {
        while (activeWorkout.session.activeDurationSetId != null) {
            durationNow = System.currentTimeMillis()
            delay(200L)
        }
    }
    val allPlannedSetsCompleted = activeWorkout.exercises
        .flatMap { it.sets }
        .filter { it.isPlanned }
        .all { it.status == SessionSetStatus.COMPLETED }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Resume workout",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = activeWorkout.session.workoutNameSnapshot,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = activeWorkout.session.programNameSnapshot,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CurrentSetFocusCard(currentSetFocus)
        OutlinedButton(onClick = { addingExercise = true }) { Text("Add exercise for today") }

        activeWorkout.exerciseDisplayBlocks().forEach { block ->
            key(when (block) {
                is ActiveWorkoutDisplayBlock.SingleExercise -> block.exercise.exercise.id
                is ActiveWorkoutDisplayBlock.Superset -> block.exercises.first().exercise.id
            }) {
                when (block) {
                    is ActiveWorkoutDisplayBlock.SingleExercise -> {
                        SessionExerciseCard(
                            exercise = block.exercise,
                            exercises = exercises,
                            currentSetId = currentSetFocus?.set?.id,
                            lastTime = block.exercise.lastTimeFrom(lastTimeByTemplateExerciseId),
                            shouldCollapseSet = { set -> activeWorkout.shouldCollapseSet(block.exercise, set) },
                            onCompleteSet = onCompleteSet,
                            durationSessionId = activeWorkout.session.id,
                            durationTimerSetId = activeWorkout.session.activeDurationSetId,
                            durationStartsAt = activeWorkout.session.durationStartsAt,
                            durationEndsAt = activeWorkout.session.durationEndsAt,
                            durationNow = durationNow,
                            onStartDurationSet = onStartDurationSet,
                            onCancelDurationSet = onCancelDurationSet,
                            onStopDurationSet = onStopDurationSet,
                            onUncompleteSet = onUncompleteSet,
                            onSkipSet = onSkipSet,
                            onAddSessionSet = onAddSessionSet,
                            onReplaceExerciseForToday = onReplaceExerciseForToday,
                            onSkipExercise = onSkipExercise,
                            onDoLater = onDoLater,
                        )
                    }
                    is ActiveWorkoutDisplayBlock.Superset -> {
                        SupersetExerciseGroup(
                            block = block,
                            activeWorkout = activeWorkout,
                            exercises = exercises,
                            currentSetId = currentSetFocus?.set?.id,
                            lastTimeByTemplateExerciseId = lastTimeByTemplateExerciseId,
                            onCompleteSet = onCompleteSet,
                            durationSessionId = activeWorkout.session.id,
                            durationTimerSetId = activeWorkout.session.activeDurationSetId,
                            durationStartsAt = activeWorkout.session.durationStartsAt,
                            durationEndsAt = activeWorkout.session.durationEndsAt,
                            durationNow = durationNow,
                            onStartDurationSet = onStartDurationSet,
                            onCancelDurationSet = onCancelDurationSet,
                            onStopDurationSet = onStopDurationSet,
                            onUncompleteSet = onUncompleteSet,
                            onSkipSet = onSkipSet,
                            onAddSessionSet = onAddSessionSet,
                            onReplaceExerciseForToday = onReplaceExerciseForToday,
                            onSkipExercise = onSkipExercise,
                            onDoLater = onDoLater,
                        )
                    }
                }
            }
        }

        if (addingExercise) {
            AddSessionExerciseDialog(
                sessionId = activeWorkout.session.id,
                exercises = exercises,
                onDismiss = { addingExercise = false },
                onAdd = onAddExercise,
            )
        }
        HorizontalDivider()

        if (!confirmingPartialFinish) {
            Button(
                onClick = {
                    if (allPlannedSetsCompleted) {
                        if (progressionReviewItems.isEmpty()) {
                            onFinishWorkout(false, emptyMap())
                        } else {
                            pendingFinishAllowsPartial = false
                            showingProgressionReview = true
                        }
                    } else {
                        confirmingPartialFinish = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Finish workout")
            }
        } else {
            Text(
                text = "Some planned sets are incomplete. Finish workout anyway?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        confirmingPartialFinish = false
                        if (progressionReviewItems.isEmpty()) {
                            onFinishWorkout(true, emptyMap())
                        } else {
                            pendingFinishAllowsPartial = true
                            showingProgressionReview = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Finish partial")
                }
                OutlinedButton(
                    onClick = { confirmingPartialFinish = false },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Keep logging")
                }
            }
        }

        HorizontalDivider()

        if (!confirmingDiscard) {
            OutlinedButton(
                onClick = { confirmingDiscard = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Discard workout")
            }
        } else {
            Text(
                text = "Discard this active workout?",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        confirmingDiscard = false
                        onDiscardWorkout()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Discard")
                }
                OutlinedButton(
                    onClick = { confirmingDiscard = false },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Keep")
                }
            }
        }

        if (showingProgressionReview) {
            FinishProgressionReviewDialog(
                items = progressionReviewItems,
                onDismiss = { showingProgressionReview = false },
                onFinish = { choices ->
                    showingProgressionReview = false
                    onFinishWorkout(pendingFinishAllowsPartial, choices)
                },
            )
        }
    }
}

@Composable
private fun CurrentSetFocusCard(
    focus: CurrentSetFocus?,
) {
    val weightUnit = LocalAppPreferences.current.weightUnit
    val palette = workoutStateColors(
        if (focus == null) WorkoutVisualState.Ready else WorkoutVisualState.Current,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = palette.container),
        border = BorderStroke(1.dp, palette.border),
    ) {
        Column(
            modifier = Modifier.padding(WorkoutSpacing.card),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusPill(
                text = if (focus == null) "Ready to finish" else "Current set",
                state = if (focus == null) WorkoutVisualState.Ready else WorkoutVisualState.Current,
            )
            if (focus == null) {
                Text(
                    text = "All planned sets are handled.",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.content,
                )
                Text(
                    text = "Review anything you changed, then finish the workout.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.content.copy(alpha = 0.78f),
                )
            } else {
                Text(
                    text = focus.exerciseName,
                    style = MaterialTheme.typography.titleLarge,
                    color = palette.content,
                )
                Text(
                    text = "${focus.set.headerText()} · " + focus.set.trackingText(focus.trackingMode, actual = false, weightUnit = weightUnit),
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.content,
                )
            }
        }
    }
}

@Composable
private fun RestTimerBottomBar(
    restEndsAt: Long?,
) {
    if (restEndsAt == null) return

    var now by remember(restEndsAt) { mutableStateOf(System.currentTimeMillis()) }

    androidx.compose.runtime.LaunchedEffect(restEndsAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val remainingMillis = restEndsAt - now
    val timerSeconds = if (remainingMillis > 0L) {
        (remainingMillis + 999L) / 1_000L
    } else {
        -(((-remainingMillis) + 999L) / 1_000L)
    }
    val sign = if (remainingMillis <= 0L) "-" else ""
    val absoluteSeconds = kotlin.math.abs(timerSeconds)
    val minutes = absoluteSeconds / 60
    val seconds = absoluteSeconds % 60
    val timerText = "Rest $sign%02d:%02d".format(minutes, seconds)
    val restState = if (remainingMillis <= 0L) WorkoutVisualState.Ready else WorkoutVisualState.Rest
    val palette = workoutStateColors(restState)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = WorkoutSpacing.screen, vertical = 8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = palette.container),
    ) {
        Column(
            modifier = Modifier.padding(WorkoutSpacing.card),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = timerText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = palette.content,
                )
                StatusPill(
                    text = if (remainingMillis <= 0L) "Ready" else "Rest",
                    state = restState,
                )
            }
        }
    }
}

@Composable
private fun FinishProgressionReviewDialog(
    items: List<FinishProgressionReviewItem>,
    onDismiss: () -> Unit,
    onFinish: (Map<Long, ProgressionFinishChoice>) -> Unit,
) {
    val weightUnit = LocalAppPreferences.current.weightUnit
    var choices by remember(items) {
        mutableStateOf(items.associate { it.sessionSetId to ProgressionFinishChoice.NO_CHANGE })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review progression") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = item.exerciseName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = item.changedSetText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Logged: " + trackingText(item.trackingMode, item.loggedWeightCentiKg, item.loggedReps, null, weightUnit),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        ProgressionChoiceButtons(
                            selected = choices.getValue(item.sessionSetId),
                            onSelect = { choice ->
                                choices = choices + (item.sessionSetId to choice)
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onFinish(choices) }) {
                Text("Finish")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep logging")
            }
        },
    )
}

@Composable
private fun ProgressionChoiceButtons(
    selected: ProgressionFinishChoice,
    onSelect: (ProgressionFinishChoice) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ProgressionChoiceButton(
            label = "No change",
            selected = selected == ProgressionFinishChoice.NO_CHANGE,
            onClick = { onSelect(ProgressionFinishChoice.NO_CHANGE) },
        )
        ProgressionChoiceButton(
            label = "Change only this set",
            selected = selected == ProgressionFinishChoice.CHANGE_THIS_SET,
            onClick = { onSelect(ProgressionFinishChoice.CHANGE_THIS_SET) },
        )
        ProgressionChoiceButton(
            label = "Set target for all sets",
            selected = selected == ProgressionFinishChoice.SET_TARGET_FOR_EXERCISE,
            onClick = { onSelect(ProgressionFinishChoice.SET_TARGET_FOR_EXERCISE) },
        )
    }
}

@Composable
private fun ProgressionChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label)
        }
    }
}

@Composable
private fun SupersetExerciseGroup(
    block: ActiveWorkoutDisplayBlock.Superset,
    activeWorkout: WorkoutSessionWithDetails,
    exercises: List<ExerciseEntity>,
    currentSetId: Long?,
    lastTimeByTemplateExerciseId: Map<Long, LastTimeExerciseContext>,
    onCompleteSet: (Long, String, String, TrackingMode) -> Unit,
    durationSessionId: Long,
    durationTimerSetId: Long?,
    durationStartsAt: Long?,
    durationEndsAt: Long?,
    durationNow: Long,
    onStartDurationSet: (Long, Long) -> Unit,
    onCancelDurationSet: (Long, Long) -> Unit,
    onStopDurationSet: (Long, Long) -> Unit,
    onUncompleteSet: (Long) -> Unit,
    onSkipSet: (Long) -> Unit,
    onAddSessionSet: (Long, SetType) -> Unit,
    onReplaceExerciseForToday: (Long, Long) -> Unit,
    onSkipExercise: (Long) -> Unit,
    onDoLater: (Long) -> Unit,
) {
    val shape = RoundedCornerShape(WorkoutRadii.card)
    val groupRestSeconds = block.exercises
        .firstNotNullOfOrNull { it.exercise.supersetRestSecondsSnapshot }
        ?: block.exercises.last().exercise.restSecondsSnapshot

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f), shape)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), shape)
            .padding(WorkoutSpacing.card),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Superset",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "${block.exercises.size} exercises · Group rest ${groupRestSeconds}s",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        block.exercises.forEachIndexed { index, exercise ->
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            }
            SessionExerciseCard(
                exercise = exercise,
                exercises = exercises,
                currentSetId = currentSetId,
                lastTime = exercise.lastTimeFrom(lastTimeByTemplateExerciseId),
                shouldCollapseSet = { set -> activeWorkout.shouldCollapseSet(exercise, set) },
                onCompleteSet = onCompleteSet,
                durationSessionId = durationSessionId,
                durationTimerSetId = durationTimerSetId,
                durationStartsAt = durationStartsAt,
                durationEndsAt = durationEndsAt,
                durationNow = durationNow,
                onStartDurationSet = onStartDurationSet,
                onCancelDurationSet = onCancelDurationSet,
                onStopDurationSet = onStopDurationSet,
                onUncompleteSet = onUncompleteSet,
                onSkipSet = onSkipSet,
                onAddSessionSet = onAddSessionSet,
                onReplaceExerciseForToday = onReplaceExerciseForToday,
                onSkipExercise = onSkipExercise,
                onDoLater = onDoLater,
                showSupersetLabel = false,
            )
        }
    }
}

@Composable
private fun SessionExerciseCard(
    exercise: SessionExerciseWithSets,
    exercises: List<ExerciseEntity>,
    currentSetId: Long?,
    lastTime: LastTimeExerciseContext?,
    shouldCollapseSet: (SessionSetEntity) -> Boolean,
    onCompleteSet: (Long, String, String, TrackingMode) -> Unit,
    durationSessionId: Long,
    durationTimerSetId: Long?,
    durationStartsAt: Long?,
    durationEndsAt: Long?,
    durationNow: Long,
    onStartDurationSet: (Long, Long) -> Unit,
    onCancelDurationSet: (Long, Long) -> Unit,
    onStopDurationSet: (Long, Long) -> Unit,
    onUncompleteSet: (Long) -> Unit,
    onSkipSet: (Long) -> Unit,
    onAddSessionSet: (Long, SetType) -> Unit,
    onReplaceExerciseForToday: (Long, Long) -> Unit,
    onSkipExercise: (Long) -> Unit,
    onDoLater: (Long) -> Unit,
    showSupersetLabel: Boolean = true,
) {
    val weightUnit = LocalAppPreferences.current.weightUnit
    val snapshot = exercise.exercise
    val isSuperset = showSupersetLabel && snapshot.supersetGroupSnapshot != null
    var showingReplacementPicker by remember(snapshot.id) { mutableStateOf(false) }
    var menuExpanded by remember(snapshot.id) { mutableStateOf(false) }
    var confirmingSkip by remember(snapshot.id) { mutableStateOf(false) }
    val hasPending = exercise.sets.any { it.status == SessionSetStatus.PENDING }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSuperset) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f))
        } else {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WorkoutSpacing.card),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = snapshot.exerciseNameSnapshot,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasPending) Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                    ) { WorkoutGlyph(WorkoutIcon.Menu, contentDescription = "Exercise actions") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (exercises.isNotEmpty()) DropdownMenuItem(
                            text = { Text("Replace for today") },
                            onClick = { menuExpanded = false; showingReplacementPicker = true },
                        )
                        DropdownMenuItem(
                            text = { Text(if (snapshot.supersetGroupSnapshot == null) "Do later" else "Do superset later") },
                            onClick = { menuExpanded = false; onDoLater(snapshot.id) },
                        )
                        DropdownMenuItem(
                            text = { Text("Skip exercise") },
                            onClick = { menuExpanded = false; confirmingSkip = true },
                        )
                    }
                }
            }
            Text(
                text = "${snapshot.plannedSetCountSnapshot} x " + trackingText(
                    snapshot.trackingModeSnapshot,
                    snapshot.prescribedWeightCentiKgSnapshot,
                    snapshot.targetRepsSnapshot,
                    snapshot.targetDurationSecondsSnapshot,
                    weightUnit,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = (if (snapshot.trackingModeSnapshot == TrackingMode.DURATION) "" else "Reps ${snapshot.repMinSnapshot}-${snapshot.repMaxSnapshot} · ") + "Rest ${snapshot.restSecondsSnapshot}s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showSupersetLabel && snapshot.supersetGroupSnapshot != null) {
                Text(
                    text = "Superset · Group rest ${snapshot.supersetRestSecondsSnapshot ?: snapshot.restSecondsSnapshot}s",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            SetupNotePanel(note = snapshot.setupNoteSnapshot)
            LastTimePanel(lastTime = lastTime)

            exercise.sets
                .sortedBy { it.setOrder }
                .forEach { set ->
                    SessionSetRow(
                        set = set,
                        trackingMode = snapshot.trackingModeSnapshot,
                        isCurrent = set.id == currentSetId,
                        collapseCompleted = shouldCollapseSet(set),
                        weightIncrementCentiKg = snapshot.incrementCentiKgSnapshot,
                        onCompleteSet = onCompleteSet,
                        durationSessionId = durationSessionId,
                        durationTimerSetId = durationTimerSetId,
                        durationStartsAt = durationStartsAt,
                        durationEndsAt = durationEndsAt,
                        durationNow = durationNow,
                        onStartDurationSet = onStartDurationSet,
                        onCancelDurationSet = onCancelDurationSet,
                        onStopDurationSet = onStopDurationSet,
                        onUncompleteSet = onUncompleteSet,
                        onSkipSet = onSkipSet,
                    )
                }

            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onAddSessionSet(snapshot.id, SetType.EXTRA) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Extra")
                }
                if (snapshot.trackingModeSnapshot != TrackingMode.DURATION) OutlinedButton(
                    onClick = { onAddSessionSet(snapshot.id, SetType.AMRAP) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("AMRAP")
                }
                if (snapshot.trackingModeSnapshot == TrackingMode.WEIGHT_REPS) OutlinedButton(
                    onClick = { onAddSessionSet(snapshot.id, SetType.DROP) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Drop")
                }
            }
        }
    }

    if (confirmingSkip) {
        AlertDialog(
            onDismissRequest = { confirmingSkip = false },
            title = { Text("Skip remaining sets?") },
            text = { Text("Skip all ${exercise.sets.count { it.status == SessionSetStatus.PENDING }} remaining sets for ${snapshot.exerciseNameSnapshot}, including warm-ups. Completed sets stay logged. This applies to today only.") },
            confirmButton = {
                TextButton(onClick = { confirmingSkip = false; onSkipExercise(snapshot.id) }) { Text("Skip exercise") }
            },
            dismissButton = { TextButton(onClick = { confirmingSkip = false }) { Text("Cancel") } },
        )
    }
    if (showingReplacementPicker) {
        ReplaceExerciseForTodayDialog(
            currentExerciseName = snapshot.exerciseNameSnapshot,
            exercises = exercises,
            onDismiss = { showingReplacementPicker = false },
            onReplace = { replacementExerciseId ->
                showingReplacementPicker = false
                onReplaceExerciseForToday(snapshot.id, replacementExerciseId)
            },
        )
    }
}

@Composable
private fun ReplaceExerciseForTodayDialog(
    currentExerciseName: String,
    exercises: List<ExerciseEntity>,
    onDismiss: () -> Unit,
    onReplace: (Long) -> Unit,
) {
    val replacements = exercises
        .filterNot { it.name == currentExerciseName }
        .sortedBy { it.name.lowercase(Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace for today") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "This changes only the active workout. The program and progression target stay unchanged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (replacements.isEmpty()) {
                    Text(
                        text = "No other active exercises in the library.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(replacements.size) { index ->
                            val exercise = replacements[index]
                            OutlinedButton(
                                onClick = { onReplace(exercise.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(exercise.name)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SetupNotePanel(
    note: String,
) {
    if (note.isBlank()) return

    val palette = workoutStateColors(WorkoutVisualState.Ready)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.container.copy(alpha = 0.72f), RoundedCornerShape(WorkoutRadii.row))
            .border(1.dp, palette.border.copy(alpha = 0.46f), RoundedCornerShape(WorkoutRadii.row))
            .padding(WorkoutSpacing.compactCard),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Setup",
            style = MaterialTheme.typography.labelLarge,
            color = palette.content,
        )
        Text(
            text = note,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.content,
        )
    }
}

@Composable
private fun LastTimePanel(
    lastTime: LastTimeExerciseContext?,
) {
    val weightUnit = LocalAppPreferences.current.weightUnit
    if (lastTime == null || lastTime.sets.isEmpty()) return

    val palette = workoutStateColors(
        if (lastTime.status == WorkoutSessionStatus.PARTIAL) WorkoutVisualState.Rest else WorkoutVisualState.Completed,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.container.copy(alpha = 0.72f), RoundedCornerShape(WorkoutRadii.row))
            .border(1.dp, palette.border.copy(alpha = 0.54f), RoundedCornerShape(WorkoutRadii.row))
            .padding(WorkoutSpacing.compactCard),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Last time",
                style = MaterialTheme.typography.labelLarge,
                color = palette.content,
            )
            Text(
                text = formatWorkoutContextDate(lastTime.completedAt),
                style = MaterialTheme.typography.labelSmall,
                color = palette.content.copy(alpha = 0.78f),
            )
        }
        Text(
            text = lastTime.workoutName,
            style = MaterialTheme.typography.bodySmall,
            color = palette.content.copy(alpha = 0.78f),
        )
        lastTime.sets.forEach { set ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = set.lastTimeLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.content,
                )
                Text(
                    text = set.lastTimeLoad(weightUnit),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.content,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SessionSetRow(
    set: SessionSetEntity,
    trackingMode: TrackingMode,
    isCurrent: Boolean,
    collapseCompleted: Boolean,
    weightIncrementCentiKg: Int,
    onCompleteSet: (Long, String, String, TrackingMode) -> Unit,
    durationSessionId: Long,
    durationTimerSetId: Long?,
    durationStartsAt: Long?,
    durationEndsAt: Long?,
    durationNow: Long,
    onStartDurationSet: (Long, Long) -> Unit,
    onCancelDurationSet: (Long, Long) -> Unit,
    onStopDurationSet: (Long, Long) -> Unit,
    onUncompleteSet: (Long) -> Unit,
    onSkipSet: (Long) -> Unit,
) {
    val weightUnit = LocalAppPreferences.current.weightUnit
    val defaultWeight = set.actualWeightCentiKg ?: set.prescribedWeightCentiKg ?: 0
    val defaultReps = if (trackingMode == TrackingMode.DURATION) set.actualDurationSeconds ?: set.prescribedDurationSeconds else set.actualReps ?: set.prescribedReps
    var weight by remember(set.id, set.actualWeightCentiKg, set.prescribedWeightCentiKg, weightUnit) {
        mutableStateOf(formatWeightValue(defaultWeight, weightUnit))
    }
    var reps by remember(set.id, set.actualReps, set.prescribedReps, set.actualDurationSeconds, set.prescribedDurationSeconds) {
        mutableStateOf(defaultReps?.toString().orEmpty())
    }
    val visualState = set.visualState(isCurrent)
    val palette = workoutStateColors(visualState)
    val rowColor by animateColorAsState(palette.container, label = "set-row-container")
    val rowContentColor by animateColorAsState(palette.content, label = "set-row-content")
    val isDropSet = set.setType == SetType.DROP
    val startPadding = if (isDropSet) 24.dp else 0.dp
    val rowShape = RoundedCornerShape(WorkoutRadii.row)
    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(start = startPadding, top = 6.dp, bottom = 6.dp)
        .background(rowColor, rowShape)
        .let { modifier ->
            if (isDropSet) {
                modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, rowShape)
            } else if (isCurrent) {
                modifier.border(1.dp, palette.border, rowShape)
            } else {
                modifier
            }
        }
        .padding(10.dp)
    var expanded by remember(set.id, set.status, set.completedAt, collapseCompleted) {
        mutableStateOf(!collapseCompleted)
    }

    if (!expanded) {
        CollapsedSessionSetRow(
            set = set,
            trackingMode = trackingMode,
            rowColor = rowColor,
            rowContentColor = rowContentColor,
            startPadding = startPadding,
            showDropBorder = isDropSet,
            showCurrentBorder = isCurrent,
            onExpand = { expanded = true },
        )
        return
    }

    Column(
        modifier = rowModifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = set.headerText(),
                style = MaterialTheme.typography.labelLarge,
                color = rowContentColor,
            )
            StatusPill(
                text = if (isCurrent) "Current" else set.status.displayName(),
                state = visualState,
            )
        }
        if (trackingMode == TrackingMode.WEIGHT_REPS) WeightAdjuster(
            label = weightUnit.symbol,
            value = weight,
            onValueChange = { weight = it },
            modifier = Modifier.fillMaxWidth(),
            incrementCentiKg = weightIncrementCentiKg,
            weightUnit = weightUnit,
        )
        val ownsDurationTimer = trackingMode == TrackingMode.DURATION && durationTimerSetId == set.id
        if (!ownsDurationTimer) {
            OutlinedTextField(
                value = reps,
                onValueChange = { reps = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (trackingMode == TrackingMode.DURATION) "seconds" else "reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        } else {
            Text(
                text = durationCountdownText(durationStartsAt, durationEndsAt, durationNow),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        when (set.status) {
            SessionSetStatus.PENDING -> {
                if (trackingMode == TrackingMode.DURATION) {
                    Button(
                        onClick = {
                            when {
                                !ownsDurationTimer -> onStartDurationSet(durationSessionId, set.id)
                                durationStartsAt != null && durationNow < durationStartsAt -> onCancelDurationSet(durationSessionId, set.id)
                                else -> onStopDurationSet(durationSessionId, set.id)
                            }
                        },
                        enabled = durationTimerSetId == null || ownsDurationTimer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                !ownsDurationTimer -> "START SET"
                                durationStartsAt != null && durationNow < durationStartsAt -> "CANCEL"
                                else -> "STOP"
                            },
                        )
                    }
                    if (!ownsDurationTimer) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onCompleteSet(set.id, weight, reps, trackingMode) },
                            enabled = durationTimerSetId == null,
                            modifier = Modifier.weight(1f),
                        ) { Text("Log manually") }
                        OutlinedButton(
                            onClick = { onSkipSet(set.id) },
                            enabled = durationTimerSetId == null,
                            modifier = Modifier.weight(1f),
                        ) { Text("Skip") }
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onCompleteSet(set.id, weight, reps, trackingMode) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Complete")
                    }
                    OutlinedButton(
                        onClick = { onSkipSet(set.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Skip")
                    }
                }
            }
            SessionSetStatus.COMPLETED -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onCompleteSet(set.id, weight, reps, trackingMode) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Save")
                    }
                    OutlinedButton(
                        onClick = { onUncompleteSet(set.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Mark pending")
                    }
                }
            }
            SessionSetStatus.SKIPPED -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onCompleteSet(set.id, weight, reps, trackingMode) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Complete")
                    }
                    OutlinedButton(
                        onClick = { onUncompleteSet(set.id) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Mark pending")
                    }
                }
            }
        }
    }
}

private fun durationCountdownText(startsAt: Long?, endsAt: Long?, now: Long): String {
    startsAt ?: return ""
    endsAt ?: return ""
    if (now < startsAt) return ceil((startsAt - now) / 1_000.0).toInt().coerceAtLeast(1).toString()
    if (now < startsAt + 1_000L) return "GO"
    return ceil((endsAt - now).coerceAtLeast(0L) / 1_000.0).toInt().toString()
}

@Composable
private fun CollapsedSessionSetRow(
    set: SessionSetEntity,
    trackingMode: TrackingMode,
    rowColor: Color,
    rowContentColor: Color,
    startPadding: Dp,
    showDropBorder: Boolean,
    showCurrentBorder: Boolean,
    onExpand: () -> Unit,
) {
    val weightUnit = LocalAppPreferences.current.weightUnit
    val rowShape = RoundedCornerShape(WorkoutRadii.row)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding, top = 6.dp, bottom = 6.dp)
            .background(rowColor, rowShape)
            .let { modifier ->
                if (showDropBorder) {
                    modifier.border(1.dp, MaterialTheme.colorScheme.tertiary, rowShape)
                } else if (showCurrentBorder) {
                    modifier.border(1.dp, MaterialTheme.colorScheme.primary, rowShape)
                } else {
                    modifier
                }
            }
            .clickable(onClick = onExpand)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = set.collapsedLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = rowContentColor,
        )
        Text(
            text = set.trackingText(trackingMode, weightUnit = weightUnit),
            style = MaterialTheme.typography.labelLarge,
            color = rowContentColor,
        )
    }
}

private fun SetType.displayName(): String = when (this) {
    SetType.WARMUP -> "Warm-up"
    SetType.WORKING -> "Set"
    SetType.EXTRA -> "Extra"
    SetType.AMRAP -> "AMRAP"
    SetType.DROP -> "Drop"
}

private fun SessionSetStatus.displayName(): String = when (this) {
    SessionSetStatus.PENDING -> "Pending"
    SessionSetStatus.COMPLETED -> "Completed"
    SessionSetStatus.SKIPPED -> "Skipped"
}

private fun LastTimeSetContext.lastTimeLabel(): String =
    when (setType) {
        SetType.WARMUP -> "Warm-up"
        SetType.WORKING -> if (status == SessionSetStatus.SKIPPED) {
            "Set ${setOrder + 1} skipped"
        } else {
            "Set ${setOrder + 1}"
        }
        SetType.EXTRA -> "Extra ${setOrder + 1}"
        SetType.AMRAP -> "AMRAP ${setOrder + 1}"
        SetType.DROP -> "Drop ${setOrder + 1}"
    }

private fun LastTimeSetContext.lastTimeLoad(weightUnit: WeightUnit): String =
    when (status) {
        SessionSetStatus.COMPLETED -> trackingText(trackingMode, weightCentiKg, reps, durationSeconds, weightUnit)
        SessionSetStatus.SKIPPED -> trackingText(trackingMode, weightCentiKg, reps, durationSeconds, weightUnit)
        SessionSetStatus.PENDING -> trackingText(trackingMode, weightCentiKg, reps, durationSeconds, weightUnit) + " pending"
    }

private fun SessionExerciseWithSets.lastTimeFrom(
    lastTimeByTemplateExerciseId: Map<Long, LastTimeExerciseContext>,
): LastTimeExerciseContext? =
    exercise.sourceWorkoutTemplateExerciseId?.let(lastTimeByTemplateExerciseId::get)

private fun formatWorkoutContextDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))

private fun SessionSetEntity.visualState(isCurrent: Boolean): WorkoutVisualState =
    when {
        isCurrent && status == SessionSetStatus.PENDING -> WorkoutVisualState.Current
        status == SessionSetStatus.PENDING -> WorkoutVisualState.Pending
        status == SessionSetStatus.COMPLETED -> WorkoutVisualState.Completed
        status == SessionSetStatus.SKIPPED -> WorkoutVisualState.Skipped
        else -> WorkoutVisualState.Disabled
    }

private fun SessionSetEntity.headerText(): String =
    when (setType) {
        SetType.WARMUP -> "${setType.displayName()} | ${status.displayName()}"
        SetType.DROP -> "${setType.displayName()} ${setOrder + 1} | Drop from previous | ${status.displayName()}"
        else -> "${setType.displayName()} ${setOrder + 1} | ${status.displayName()}"
    }

private fun SessionSetEntity.collapsedLabel(): String =
    when (setType) {
        SetType.WARMUP -> setType.displayName()
        SetType.DROP -> "${setType.displayName()} ${setOrder + 1} | Drop"
        else -> "${setType.displayName()} ${setOrder + 1}"
    }

private sealed interface ActiveWorkoutDisplayBlock {
    data class SingleExercise(val exercise: SessionExerciseWithSets) : ActiveWorkoutDisplayBlock

    data class Superset(
        val groupId: Long,
        val exercises: List<SessionExerciseWithSets>,
    ) : ActiveWorkoutDisplayBlock
}

private fun WorkoutSessionWithDetails.exerciseDisplayBlocks(): List<ActiveWorkoutDisplayBlock> {
    val sortedExercises = exercises.sortedBy { it.exercise.sortOrderSnapshot }
    val seenSupersetGroups = mutableSetOf<Long>()

    return buildList {
        sortedExercises.forEach { exercise ->
            val supersetGroup = exercise.exercise.supersetGroupSnapshot
            if (supersetGroup == null) {
                add(ActiveWorkoutDisplayBlock.SingleExercise(exercise))
                return@forEach
            }
            if (seenSupersetGroups.add(supersetGroup)) {
                add(
                    ActiveWorkoutDisplayBlock.Superset(
                        groupId = supersetGroup,
                        exercises = sortedExercises.filter {
                            it.exercise.supersetGroupSnapshot == supersetGroup
                        },
                    ),
                )
            }
        }
    }
}

private fun WorkoutSessionWithDetails.shouldCollapseSet(
    exercise: SessionExerciseWithSets,
    set: SessionSetEntity,
): Boolean {
    if (set.status != SessionSetStatus.COMPLETED) return false
    if (!set.isPlanned) return true

    val supersetGroup = exercise.exercise.supersetGroupSnapshot ?: return true
    val groupExercises = exercises.filter { it.exercise.supersetGroupSnapshot == supersetGroup }
    if (groupExercises.size < 2) return true

    return groupExercises.all { groupExercise ->
        groupExercise.sets.any { groupSet ->
            groupSet.isPlanned &&
                groupSet.setOrder == set.setOrder &&
                groupSet.status == SessionSetStatus.COMPLETED
        }
    }
}

private fun WorkoutSessionWithDetails.progressionReviewItems(weightUnit: WeightUnit): List<FinishProgressionReviewItem> =
    exercises
        .sortedBy { it.exercise.sortOrderSnapshot }
        .flatMap { exercise ->
            exercise.sets
                .filter {
                    it.countsForProgression &&
                        it.status == SessionSetStatus.COMPLETED &&
                        (it.actualWeightCentiKg != it.prescribedWeightCentiKg ||
                            it.actualReps != it.prescribedReps)
                }
                .sortedBy { it.setOrder }
                .map { set ->
                    FinishProgressionReviewItem(
                        sessionSetId = set.id,
                        exerciseName = exercise.exercise.exerciseNameSnapshot,
                        trackingMode = exercise.exercise.trackingModeSnapshot,
                        changedSetText = "Set ${set.setOrder + 1}: " +
                            set.trackingText(exercise.exercise.trackingModeSnapshot, actual = false, weightUnit = weightUnit) + " -> " +
                            set.trackingText(exercise.exercise.trackingModeSnapshot, weightUnit = weightUnit),
                        loggedWeightCentiKg = set.actualWeightCentiKg
                            ?: exercise.exercise.prescribedWeightCentiKgSnapshot,
                        loggedReps = set.actualReps ?: exercise.exercise.targetRepsSnapshot,
                    )
                }
        }

private data class FinishProgressionReviewItem(
    val sessionSetId: Long,
    val exerciseName: String,
    val changedSetText: String,
    val loggedWeightCentiKg: Int,
    val loggedReps: Int,
    val trackingMode: TrackingMode,
)

private data class CurrentSetFocus(
    val exerciseName: String,
    val set: SessionSetEntity,
    val trackingMode: TrackingMode,
)

private fun WorkoutSessionWithDetails.currentSetFocus(): CurrentSetFocus? {
    val actionable = WorkoutNotificationProjector.nextActionableSet(this) ?: return null
    val set = exercises.flatMap { it.sets }.first { it.id == actionable.setId }
    return CurrentSetFocus(actionable.exerciseName, set, actionable.trackingMode)
}
