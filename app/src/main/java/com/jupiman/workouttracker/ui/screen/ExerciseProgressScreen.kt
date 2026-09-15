package com.jupiman.workouttracker.ui.screen

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.TrackingMode
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.model.ExerciseProgressTrack
import com.jupiman.workouttracker.data.repository.ExerciseProgressSession
import com.jupiman.workouttracker.data.repository.formatWeight
import com.jupiman.workouttracker.data.repository.trackingText
import com.jupiman.workouttracker.preferences.WeightUnit
import com.jupiman.workouttracker.ui.LocalAppPreferences
import com.jupiman.workouttracker.ui.theme.StatusPill
import com.jupiman.workouttracker.ui.theme.WorkoutRadii
import com.jupiman.workouttracker.ui.theme.WorkoutSpacing
import com.jupiman.workouttracker.ui.theme.WorkoutVisualState
import com.jupiman.workouttracker.ui.theme.WorkoutEmptyState
import com.jupiman.workouttracker.ui.theme.WorkoutGlyph
import com.jupiman.workouttracker.ui.theme.WorkoutIcon
import com.jupiman.workouttracker.ui.theme.WorkoutScreenHeader
import com.jupiman.workouttracker.ui.viewmodel.ProgramViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun ExerciseProgressScreen(
    exercise: ExerciseEntity,
    viewModel: ProgramViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tracksFlow = remember(exercise.id) { viewModel.progressTracks(exercise.id) }
    ExerciseProgressScreen(
        exerciseName = exercise.name,
        tracksFlow = tracksFlow,
        sessionsForTrack = viewModel::progressSessions,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun ExerciseProgressScreen(
    exerciseName: String,
    tracksFlow: Flow<List<ExerciseProgressTrack>>,
    sessionsForTrack: (Long) -> Flow<List<ExerciseProgressSession>>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialTrackId: Long? = null,
    backContentDescription: String = "Back to exercise library",
) {
    val tracks by tracksFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedTrackId by rememberSaveable(exerciseName, initialTrackId) { mutableStateOf(initialTrackId) }
    val selectedTrack = tracks.firstOrNull { it.workoutTemplateExerciseId == selectedTrackId }
        ?: tracks.firstOrNull()

    LaunchedEffect(tracks, selectedTrackId) {
        if (selectedTrackId == null || tracks.none { it.workoutTemplateExerciseId == selectedTrackId }) {
            selectedTrackId = tracks.firstOrNull()?.workoutTemplateExerciseId
        }
    }

    val sessionsFlow = remember(selectedTrack?.workoutTemplateExerciseId) {
        selectedTrack?.let {
            sessionsForTrack(it.workoutTemplateExerciseId)
        } ?: flowOf(emptyList())
    }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = WorkoutSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.section),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            WorkoutScreenHeader(
                title = exerciseName,
                eyebrow = "Exercise progress",
                navigation = {
                    IconButton(onClick = onBack) {
                        WorkoutGlyph(WorkoutIcon.Back, contentDescription = backContentDescription)
                    }
                },
            )
        }
        if (tracks.isEmpty()) {
            item {
                WorkoutEmptyState(
                    title = "No progression track",
                    body = "Add this exercise to a training day to start a separate progress history for that configuration.",
                )
            }
        } else {
            if (tracks.size > 1) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Progression track", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        tracks.forEach { track ->
                            FilterChip(
                                selected = track.workoutTemplateExerciseId == selectedTrack?.workoutTemplateExerciseId,
                                onClick = { selectedTrackId = track.workoutTemplateExerciseId },
                                label = { Text("${track.programName} · ${track.workoutName}", maxLines = 2) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
            selectedTrack?.let { track ->
                item { CurrentProgressTarget(track) }
                item {
                    Text("Progress", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                item { ProgressChartCard(track = track, sessions = sessions) }
                item {
                    Text("Recent sessions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                if (sessions.isEmpty()) {
                    item {
                        WorkoutEmptyState(
                            title = "No completed history yet",
                            body = "Complete this exercise in ${track.workoutName} to add the first point.",
                        )
                    }
                } else {
                    sessions.take(10).forEach { session ->
                        item(key = session.sessionId) { ProgressSessionCard(session) }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun CurrentProgressTarget(track: ExerciseProgressTrack) {
    val weightUnit = LocalAppPreferences.current.weightUnit
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(WorkoutSpacing.card), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${track.programName} · ${track.workoutName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Current target: " + trackingText(
                    track.trackingMode,
                    track.currentWeightCentiKg,
                    track.currentTargetReps,
                    track.targetDurationSeconds,
                    weightUnit,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                if (track.trackingMode == TrackingMode.DURATION) {
                    "${track.plannedWorkingSets} sets"
                } else {
                    "${track.plannedWorkingSets} sets · ${track.repMin}-${track.repMax} reps"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
private fun ProgressChartCard(track: ExerciseProgressTrack, sessions: List<ExerciseProgressSession>) {
    val weightUnit = LocalAppPreferences.current.weightUnit
    val points = sessions.filter {
        it.trackingMode == track.trackingMode && it.graphValue != null
    }.take(12).reversed()
    val metric = when (track.trackingMode) {
        TrackingMode.WEIGHT_REPS -> "Top completed progression-set weight"
        TrackingMode.REPS -> "Best completed planned-set reps"
        TrackingMode.DURATION -> "Best completed planned-set duration"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(WorkoutSpacing.card), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(metric, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (points.size < 2) {
                Text(
                    if (points.isEmpty()) "No qualifying completed sets yet." else "Complete another session to draw a trend.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val lineColor = MaterialTheme.colorScheme.primary
                val axisColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                val values = points.map { it.graphValue!! }
                val minValue = values.min()
                val maxValue = values.max()
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .semantics { contentDescription = "$metric over ${points.size} sessions" },
                ) {
                    val left = 10.dp.toPx()
                    val right = size.width - 10.dp.toPx()
                    val top = 12.dp.toPx()
                    val bottom = size.height - 12.dp.toPx()
                    drawLine(axisColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1.dp.toPx())
                    val path = Path()
                    values.forEachIndexed { index, value ->
                        val x = left + (right - left) * index / (values.size - 1).toFloat()
                        val ratio = if (maxValue == minValue) 0.5f else (value - minValue).toFloat() / (maxValue - minValue)
                        val y = bottom - (bottom - top) * ratio
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                    values.forEachIndexed { index, value ->
                        val x = left + (right - left) * index / (values.size - 1).toFloat()
                        val ratio = if (maxValue == minValue) 0.5f else (value - minValue).toFloat() / (maxValue - minValue)
                        val y = bottom - (bottom - top) * ratio
                        drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatGraphValue(minValue, track.trackingMode, weightUnit), style = MaterialTheme.typography.labelSmall)
                    Text(formatGraphValue(maxValue, track.trackingMode, weightUnit), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ProgressSessionCard(session: ExerciseProgressSession) {
    val weightUnit = LocalAppPreferences.current.weightUnit
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(WorkoutSpacing.card), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(formatProgressDate(session.completedAt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(session.workoutName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(
                    text = if (session.status == WorkoutSessionStatus.PARTIAL) "Partial" else "Completed",
                    state = if (session.status == WorkoutSessionStatus.PARTIAL) WorkoutVisualState.Rest else WorkoutVisualState.Completed,
                )
            }
            HorizontalDivider()
            session.sets.forEach { set ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(set.progressLabel(), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        set.progressValue(session.trackingMode, weightUnit),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (set.status == SessionSetStatus.SKIPPED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun formatGraphValue(value: Int, mode: TrackingMode, weightUnit: WeightUnit): String = when (mode) {
    TrackingMode.WEIGHT_REPS -> formatWeight(value, weightUnit)
    TrackingMode.REPS -> "$value reps"
    TrackingMode.DURATION -> "$value sec"
}

private fun SessionSetEntity.progressLabel(): String = when (setType) {
    SetType.WARMUP -> "Warm-up"
    SetType.WORKING -> "Set ${setOrder + 1}"
    SetType.EXTRA -> "Extra"
    SetType.AMRAP -> "AMRAP"
    SetType.DROP -> "Drop"
}

private fun SessionSetEntity.progressValue(mode: TrackingMode, weightUnit: WeightUnit): String = when (status) {
    SessionSetStatus.SKIPPED -> "Skipped"
    SessionSetStatus.PENDING -> "Pending"
    SessionSetStatus.COMPLETED -> trackingText(mode, actualWeightCentiKg, actualReps, actualDurationSeconds, weightUnit)
}

private fun formatProgressDate(timestamp: Long): String = Instant.ofEpochMilli(timestamp)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()))
