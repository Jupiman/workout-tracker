package com.jupiman.workouttracker.ui.screen

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.model.SessionExerciseWithSets
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import com.jupiman.workouttracker.data.repository.formatCentiKg
import com.jupiman.workouttracker.ui.theme.StatusPill
import com.jupiman.workouttracker.ui.theme.WorkoutRadii
import com.jupiman.workouttracker.ui.theme.WorkoutSpacing
import com.jupiman.workouttracker.ui.theme.WorkoutVisualState
import com.jupiman.workouttracker.ui.theme.workoutStateColors
import com.jupiman.workouttracker.ui.viewmodel.HistoryViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedSession = uiState.selectedSession

    if (selectedSession != null) {
        BackHandler(onBack = viewModel::closeDetails)
        WorkoutHistoryDetail(
            sessionDetails = selectedSession,
            onBack = viewModel::closeDetails,
            modifier = modifier,
        )
    } else {
        WorkoutHistoryList(
            sessions = uiState.sessions,
            onSelectSession = viewModel::selectSession,
            onExportBackup = onExportBackup,
            onRestoreBackup = onRestoreBackup,
            modifier = modifier,
        )
    }
}

@Composable
private fun WorkoutHistoryList(
    sessions: List<WorkoutSessionWithDetails>,
    onSelectSession: (Long) -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingRestore by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = WorkoutSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        item {
            BackupCard(
                onExportBackup = onExportBackup,
                onRestoreBackup = { confirmingRestore = true },
            )
        }

        if (sessions.isEmpty()) {
            item {
                Text(
                    text = "No completed workouts yet.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            sessions.forEach { sessionDetails ->
                item(key = sessionDetails.session.id) {
                    HistorySessionRow(
                        sessionDetails = sessionDetails,
                        onClick = { onSelectSession(sessionDetails.session.id) },
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (confirmingRestore) {
        AlertDialog(
            onDismissRequest = { confirmingRestore = false },
            title = { Text("Restore backup?") },
            text = {
                Text("This replaces the local Workout Companion data on this device with the selected backup file.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmingRestore = false
                        onRestoreBackup()
                    },
                ) {
                    Text("Choose backup")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRestore = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun BackupCard(
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(WorkoutSpacing.card),
            verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item),
        ) {
            Text(
                text = "Backup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Export or restore a local JSON backup of programs, history, and active workout state.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(WorkoutSpacing.item)) {
                Button(
                    onClick = onExportBackup,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Export")
                }
                OutlinedButton(
                    onClick = onRestoreBackup,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Restore")
                }
            }
        }
    }
}

@Composable
private fun HistorySessionRow(
    sessionDetails: WorkoutSessionWithDetails,
    onClick: () -> Unit,
) {
    val session = sessionDetails.session

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(WorkoutSpacing.card),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusPill(
                text = if (session.status == WorkoutSessionStatus.PARTIAL) "Partial" else "Completed",
                state = if (session.status == WorkoutSessionStatus.PARTIAL) {
                    WorkoutVisualState.Rest
                } else {
                    WorkoutVisualState.Completed
                },
            )
            Text(
                text = session.workoutNameSnapshot,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatHistoryDate(session.completedAt ?: session.startedAt),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = historySummary(sessionDetails),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WorkoutHistoryDetail(
    sessionDetails: WorkoutSessionWithDetails,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = sessionDetails.session

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = WorkoutSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = session.workoutNameSnapshot,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = session.programNameSnapshot,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${formatHistoryDate(session.completedAt ?: session.startedAt)} · " +
                        "${formatHistoryTime(session.startedAt)}-${formatHistoryTime(session.completedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session.status == WorkoutSessionStatus.PARTIAL) {
                    StatusPill(
                        text = "Partial workout",
                        state = WorkoutVisualState.Rest,
                    )
                }
            }
        }

        sessionDetails.exercises
            .sortedBy { it.exercise.sortOrderSnapshot }
            .forEach { exercise ->
                item(key = exercise.exercise.id) {
                    HistoryExerciseCard(exercise = exercise)
                }
            }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HistoryExerciseCard(
    exercise: SessionExerciseWithSets,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(WorkoutSpacing.card),
            verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item),
        ) {
            Text(
                text = exercise.exercise.exerciseNameSnapshot,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${exercise.exercise.plannedSetCountSnapshot} x " +
                    "${exercise.exercise.targetRepsSnapshot} @ " +
                    "${formatCentiKg(exercise.exercise.prescribedWeightCentiKgSnapshot)} kg",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (exercise.exercise.supersetGroupSnapshot != null) {
                StatusPill(
                    text = "Superset",
                    state = WorkoutVisualState.Current,
                )
            }
            HorizontalDivider()
            exercise.sets
                .sortedBy { it.setOrder }
                .forEach { set ->
                    HistorySetLine(set = set)
                }
        }
    }
}

@Composable
private fun HistorySetLine(
    set: SessionSetEntity,
) {
    val state = when (set.status) {
        SessionSetStatus.COMPLETED -> WorkoutVisualState.Completed
        SessionSetStatus.SKIPPED -> WorkoutVisualState.Skipped
        SessionSetStatus.PENDING -> WorkoutVisualState.Pending
    }
    val palette = workoutStateColors(state)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.container, RoundedCornerShape(WorkoutRadii.row))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = set.historyLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.content,
        )
        Text(
            text = set.historyLoad(),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun historySummary(sessionDetails: WorkoutSessionWithDetails): String {
    val completedSets = sessionDetails.exercises
        .flatMap { it.sets }
        .count { it.status == SessionSetStatus.COMPLETED }
    val exerciseCount = sessionDetails.exercises.size
    val status = if (sessionDetails.session.status == WorkoutSessionStatus.PARTIAL) {
        "partial"
    } else {
        "completed"
    }

    return "$exerciseCount exercise(s), $completedSets set(s), $status"
}

private fun SessionSetEntity.historyLabel(): String =
    when (setType) {
        SetType.WARMUP -> "Warm-up"
        SetType.WORKING -> if (isPlanned) "Set ${setOrder + 1}" else "Extra ${setOrder + 1}"
        SetType.EXTRA -> "Extra ${setOrder + 1}"
        SetType.AMRAP -> "AMRAP ${setOrder + 1}"
        SetType.DROP -> "Drop ${setOrder + 1}"
    }

private fun SessionSetEntity.historyLoad(): String =
    when (status) {
        SessionSetStatus.COMPLETED -> {
            val weight = actualWeightCentiKg ?: prescribedWeightCentiKg
            val reps = actualReps ?: prescribedReps
            "${weight.kgText()} x ${reps?.toString() ?: "-"}"
        }
        SessionSetStatus.SKIPPED -> "${prescribedWeightCentiKg.kgText()} x " +
            "${prescribedReps?.toString() ?: "-"} skipped"
        SessionSetStatus.PENDING -> "${prescribedWeightCentiKg.kgText()} x " +
            "${prescribedReps?.toString() ?: "-"} pending"
    }

private fun Int?.kgText(): String = this?.let { "${formatCentiKg(it)} kg" } ?: "- kg"

private fun formatHistoryDate(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))

private fun formatHistoryTime(timestamp: Long?): String =
    timestamp?.let {
        Instant.ofEpochMilli(it)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    } ?: "--:--"
