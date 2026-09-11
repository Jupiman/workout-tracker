package com.jupiman.workouttracker.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.jupiman.workouttracker.ui.viewmodel.HistoryViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
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
            modifier = modifier,
        )
    }
}

@Composable
private fun WorkoutHistoryList(
    sessions: List<WorkoutSessionWithDetails>,
    onSelectSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "History",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
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
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = formatHistoryDate(session.completedAt ?: session.startedAt),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = session.workoutNameSnapshot,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = historySummary(sessionDetails),
                style = MaterialTheme.typography.bodyMedium,
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
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                )
                Text(
                    text = "${formatHistoryDate(session.completedAt ?: session.startedAt)} | " +
                        "${formatHistoryTime(session.startedAt)}-${formatHistoryTime(session.completedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (session.status == WorkoutSessionStatus.PARTIAL) {
                    Text(
                        text = "Partial workout",
                        style = MaterialTheme.typography.labelLarge,
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
            )
            HorizontalDivider()
            exercise.sets
                .sortedBy { it.setOrder }
                .forEach { set ->
                    Text(
                        text = set.historyLine(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
        }
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

private fun SessionSetEntity.historyLine(): String {
    val label = when (setType) {
        SetType.WORKING -> if (isPlanned) "" else "EXTRA "
        SetType.EXTRA -> "EXTRA "
        SetType.AMRAP -> "AMRAP "
        SetType.DROP -> "DROP "
    }

    return when (status) {
        SessionSetStatus.COMPLETED -> {
            val weight = actualWeightCentiKg ?: prescribedWeightCentiKg
            val reps = actualReps ?: prescribedReps
            "$label${weight.kgText()} x ${reps?.toString() ?: "-"}"
        }
        SessionSetStatus.SKIPPED -> "$label${prescribedWeightCentiKg.kgText()} x " +
            "${prescribedReps?.toString() ?: "-"} skipped"
        SessionSetStatus.PENDING -> "$label${prescribedWeightCentiKg.kgText()} x " +
            "${prescribedReps?.toString() ?: "-"} pending"
    }.trim()
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
