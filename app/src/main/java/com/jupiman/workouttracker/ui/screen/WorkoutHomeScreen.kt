package com.jupiman.workouttracker.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.model.SessionExerciseWithSets
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import com.jupiman.workouttracker.data.repository.formatCentiKg
import com.jupiman.workouttracker.data.repository.ProgressionFinishChoice
import com.jupiman.workouttracker.ui.viewmodel.HomeUiState
import com.jupiman.workouttracker.ui.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import kotlin.math.max

@Composable
fun WorkoutHomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val currentMessage = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(currentMessage)
        viewModel.clearMessage()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Workout",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            val activeWorkout = uiState.activeWorkout
            if (activeWorkout == null) {
                item {
                    StartWorkoutPanel(
                        uiState = uiState,
                        onStartWorkout = viewModel::startWorkout,
                    )
                }
            } else {
                item {
                    ActiveWorkoutPanel(
                        activeWorkout = activeWorkout,
                        onCompleteSet = viewModel::completeSet,
                        onUncompleteSet = viewModel::uncompleteSet,
                        onSkipSet = viewModel::skipSet,
                        onDiscardWorkout = viewModel::discardActiveWorkout,
                        onFinishWorkout = viewModel::finishActiveWorkout,
                        onAddRestTime = viewModel::addRestTime,
                        onSkipRest = viewModel::skipRest,
                        onAddSessionSet = viewModel::addSessionSet,
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Active program",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = uiState.activeProgram?.name ?: "No active program yet",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Next workout",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = uiState.nextWorkoutName ?: "No workout templates yet",
            style = MaterialTheme.typography.titleMedium,
        )
        Button(
            onClick = { onStartWorkout(uiState.nextWorkoutTemplateId) },
            enabled = uiState.nextWorkoutTemplateId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start workout")
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
    onCompleteSet: (Long, String, String) -> Unit,
    onUncompleteSet: (Long) -> Unit,
    onSkipSet: (Long) -> Unit,
    onDiscardWorkout: () -> Unit,
    onFinishWorkout: (Boolean, Map<Long, ProgressionFinishChoice>) -> Unit,
    onAddRestTime: (Int) -> Unit,
    onSkipRest: () -> Unit,
    onAddSessionSet: (Long, SetType) -> Unit,
) {
    var confirmingDiscard by remember { mutableStateOf(false) }
    var confirmingPartialFinish by remember { mutableStateOf(false) }
    var showingProgressionReview by remember { mutableStateOf(false) }
    var pendingFinishAllowsPartial by remember { mutableStateOf(false) }
    val progressionReviewItems = activeWorkout.progressionReviewItems()
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
        )

        RestTimerBanner(
            restEndsAt = activeWorkout.session.restEndsAt,
            onAddRestTime = onAddRestTime,
            onSkipRest = onSkipRest,
        )

        activeWorkout.exercises
            .sortedBy { it.exercise.sortOrderSnapshot }
            .forEach { exercise ->
                SessionExerciseCard(
                    exercise = exercise,
                    onCompleteSet = onCompleteSet,
                    onUncompleteSet = onUncompleteSet,
                    onSkipSet = onSkipSet,
                    onAddSessionSet = onAddSessionSet,
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
private fun RestTimerBanner(
    restEndsAt: Long?,
    onAddRestTime: (Int) -> Unit,
    onSkipRest: () -> Unit,
) {
    if (restEndsAt == null) return

    var now by remember(restEndsAt) { mutableStateOf(System.currentTimeMillis()) }

    androidx.compose.runtime.LaunchedEffect(restEndsAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val remainingMillis = max(0L, restEndsAt - now)
    val remainingSeconds = remainingMillis / 1_000L
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val timerText = if (remainingMillis == 0L) {
        "Rest finished"
    } else {
        "Rest %02d:%02d".format(minutes, seconds)
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = timerText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAddRestTime(30) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("+30 sec")
                }
                OutlinedButton(
                    onClick = onSkipRest,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Skip")
                }
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
    var choices by remember(items) {
        mutableStateOf(items.associate { it.sessionExerciseId to ProgressionFinishChoice.AUTOMATIC })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review progression") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = item.exerciseName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = item.changedSetsText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Logged target: ${formatCentiKg(item.loggedTargetWeightCentiKg)} kg x " +
                                item.loggedTargetReps,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        ProgressionChoiceButtons(
                            selected = choices.getValue(item.sessionExerciseId),
                            onSelect = { choice ->
                                choices = choices + (item.sessionExerciseId to choice)
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
            label = "Automatic",
            selected = selected == ProgressionFinishChoice.AUTOMATIC,
            onClick = { onSelect(ProgressionFinishChoice.AUTOMATIC) },
        )
        ProgressionChoiceButton(
            label = "No progression",
            selected = selected == ProgressionFinishChoice.NO_PROGRESSION,
            onClick = { onSelect(ProgressionFinishChoice.NO_PROGRESSION) },
        )
        ProgressionChoiceButton(
            label = "Set new target",
            selected = selected == ProgressionFinishChoice.SET_TARGET_FROM_LOGGED,
            onClick = { onSelect(ProgressionFinishChoice.SET_TARGET_FROM_LOGGED) },
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
private fun SessionExerciseCard(
    exercise: SessionExerciseWithSets,
    onCompleteSet: (Long, String, String) -> Unit,
    onUncompleteSet: (Long) -> Unit,
    onSkipSet: (Long) -> Unit,
    onAddSessionSet: (Long, SetType) -> Unit,
) {
    val snapshot = exercise.exercise
    val isSuperset = snapshot.supersetGroupSnapshot != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isSuperset) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = snapshot.exerciseNameSnapshot,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${snapshot.plannedSetCountSnapshot} x ${snapshot.targetRepsSnapshot} @ " +
                    "${formatCentiKg(snapshot.prescribedWeightCentiKgSnapshot)} kg",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Reps ${snapshot.repMinSnapshot}-${snapshot.repMaxSnapshot} | Rest ${snapshot.restSecondsSnapshot}s",
                style = MaterialTheme.typography.bodySmall,
            )
            if (snapshot.supersetGroupSnapshot != null) {
                Text(
                    text = "Superset | Group rest ${snapshot.supersetRestSecondsSnapshot ?: snapshot.restSecondsSnapshot}s",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            exercise.sets
                .sortedBy { it.setOrder }
                .forEach { set ->
                    SessionSetRow(
                        set = set,
                        onCompleteSet = onCompleteSet,
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
                OutlinedButton(
                    onClick = { onAddSessionSet(snapshot.id, SetType.AMRAP) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("AMRAP")
                }
                OutlinedButton(
                    onClick = { onAddSessionSet(snapshot.id, SetType.DROP) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Drop")
                }
            }
        }
    }
}

@Composable
private fun SessionSetRow(
    set: SessionSetEntity,
    onCompleteSet: (Long, String, String) -> Unit,
    onUncompleteSet: (Long) -> Unit,
    onSkipSet: (Long) -> Unit,
) {
    val defaultWeight = set.actualWeightCentiKg ?: set.prescribedWeightCentiKg ?: 0
    val defaultReps = set.actualReps ?: set.prescribedReps
    var weight by remember(set.id, set.actualWeightCentiKg, set.prescribedWeightCentiKg) {
        mutableStateOf(formatCentiKg(defaultWeight))
    }
    var reps by remember(set.id, set.actualReps, set.prescribedReps) {
        mutableStateOf(defaultReps?.toString().orEmpty())
    }
    val rowColor = when (set.status) {
        SessionSetStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
        SessionSetStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
        SessionSetStatus.SKIPPED -> MaterialTheme.colorScheme.errorContainer
    }
    val rowContentColor = when (set.status) {
        SessionSetStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        SessionSetStatus.COMPLETED -> MaterialTheme.colorScheme.onPrimaryContainer
        SessionSetStatus.SKIPPED -> MaterialTheme.colorScheme.onErrorContainer
    }
    val startPadding = if (set.setType == SetType.DROP) 24.dp else 0.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding, top = 6.dp, bottom = 6.dp)
            .background(rowColor, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${set.setType.displayName()} ${set.setOrder + 1} | ${set.status.displayName()}",
            style = MaterialTheme.typography.labelLarge,
            color = rowContentColor,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                modifier = Modifier.weight(1f),
                label = { Text("kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                value = reps,
                onValueChange = { reps = it },
                modifier = Modifier.weight(1f),
                label = { Text("reps") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }

        when (set.status) {
            SessionSetStatus.PENDING -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onCompleteSet(set.id, weight, reps) },
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
                        onClick = { onCompleteSet(set.id, weight, reps) },
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
                        onClick = { onCompleteSet(set.id, weight, reps) },
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

private fun SetType.displayName(): String = when (this) {
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

private fun WorkoutSessionWithDetails.progressionReviewItems(): List<FinishProgressionReviewItem> =
    exercises
        .sortedBy { it.exercise.sortOrderSnapshot }
        .mapNotNull { exercise ->
            val changedSets = exercise.sets
                .filter {
                    it.countsForProgression &&
                        it.status == SessionSetStatus.COMPLETED &&
                        (it.actualWeightCentiKg != it.prescribedWeightCentiKg ||
                            it.actualReps != it.prescribedReps)
                }
                .sortedBy { it.setOrder }
            if (changedSets.isEmpty()) return@mapNotNull null

            val bestSet = changedSets.maxWith(
                compareBy<SessionSetEntity> { it.actualWeightCentiKg ?: 0 }
                    .thenBy { it.actualReps ?: 0 }
                    .thenBy { it.setOrder },
            )
            FinishProgressionReviewItem(
                sessionExerciseId = exercise.exercise.id,
                exerciseName = exercise.exercise.exerciseNameSnapshot,
                changedSetsText = changedSets.joinToString { set ->
                    "${set.setOrder + 1}: ${formatCentiKg(set.prescribedWeightCentiKg ?: 0)} kg x " +
                        "${set.prescribedReps ?: "-"} -> " +
                        "${formatCentiKg(set.actualWeightCentiKg ?: 0)} kg x ${set.actualReps ?: "-"}"
                },
                loggedTargetWeightCentiKg = bestSet.actualWeightCentiKg
                    ?: exercise.exercise.prescribedWeightCentiKgSnapshot,
                loggedTargetReps = (bestSet.actualReps ?: exercise.exercise.targetRepsSnapshot)
                    .coerceIn(exercise.exercise.repMinSnapshot, exercise.exercise.repMaxSnapshot),
            )
        }

private data class FinishProgressionReviewItem(
    val sessionExerciseId: Long,
    val exerciseName: String,
    val changedSetsText: String,
    val loggedTargetWeightCentiKg: Int,
    val loggedTargetReps: Int,
)
