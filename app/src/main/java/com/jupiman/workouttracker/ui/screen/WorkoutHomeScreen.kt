package com.jupiman.workouttracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.model.SessionExerciseWithSets
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import com.jupiman.workouttracker.data.repository.formatCentiKg
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
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

        message?.let { currentMessage ->
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = currentMessage,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = viewModel::clearMessage) {
                            Text("Dismiss")
                        }
                    }
                }
            }
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
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
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
    onFinishWorkout: (Boolean) -> Unit,
    onAddRestTime: (Int) -> Unit,
    onSkipRest: () -> Unit,
) {
    var confirmingDiscard by remember { mutableStateOf(false) }
    var confirmingPartialFinish by remember { mutableStateOf(false) }
    val allPlannedSetsCompleted = activeWorkout.exercises
        .flatMap { it.sets }
        .filter { it.isPlanned }
        .all { it.status == SessionSetStatus.COMPLETED }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Active workout",
            style = MaterialTheme.typography.labelLarge,
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
                )
            }

        HorizontalDivider()

        if (!confirmingPartialFinish) {
            Button(
                onClick = {
                    if (allPlannedSetsCompleted) {
                        onFinishWorkout(false)
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
                        onFinishWorkout(true)
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
private fun SessionExerciseCard(
    exercise: SessionExerciseWithSets,
    onCompleteSet: (Long, String, String) -> Unit,
    onUncompleteSet: (Long) -> Unit,
    onSkipSet: (Long) -> Unit,
) {
    val snapshot = exercise.exercise

    Card(modifier = Modifier.fillMaxWidth()) {
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
    val defaultReps = set.actualReps ?: set.prescribedReps ?: 0
    var weight by remember(set.id, set.actualWeightCentiKg, set.prescribedWeightCentiKg) {
        mutableStateOf(formatCentiKg(defaultWeight))
    }
    var reps by remember(set.id, set.actualReps, set.prescribedReps) {
        mutableStateOf(defaultReps.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Set ${set.setOrder + 1} ${set.status.name.lowercase()}",
            style = MaterialTheme.typography.labelLarge,
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
