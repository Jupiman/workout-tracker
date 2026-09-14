package com.jupiman.workouttracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jupiman.workouttracker.data.repository.CompletionTarget
import com.jupiman.workouttracker.data.repository.WorkoutCompletionSummary
import com.jupiman.workouttracker.data.repository.formatCentiKg
import com.jupiman.workouttracker.ui.theme.WorkoutSpacing
import com.jupiman.workouttracker.ui.theme.StatusPill
import com.jupiman.workouttracker.ui.theme.WorkoutVisualState

@Composable
internal fun WorkoutCompletionPanel(summary: WorkoutCompletionSummary, onDone: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.section)) {
        StatusPill(text = "Completed", state = WorkoutVisualState.Completed)
        Text("Workout complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
            ),
        ) {
            Column(Modifier.padding(WorkoutSpacing.card), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(summary.workoutName, style = MaterialTheme.typography.titleLarge)
                Text(if (summary.durationSeconds < 60) "Less than a minute" else "${summary.durationSeconds / 60} min")
                Text("${summary.completedWorkingSets} / ${summary.totalWorkingSets} working sets completed")
                if (summary.partial) StatusPill(text = "Partial workout", state = WorkoutVisualState.Rest)
                if (summary.skippedSets > 0) {
                    Text("${summary.skippedSets} skipped ${if (summary.skippedSets == 1) "set" else "sets"} (including warm-ups)")
                }
            }
        }
        Text("Next time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        summary.exercises.forEach { exercise ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(WorkoutSpacing.card), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                    val before = exercise.before
                    val after = exercise.after
                    when {
                        before == null || after == null -> Text("No permanent progression track", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        before == after -> {
                            Text("No change", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TargetList(after)
                        }
                        before.distinct().size == 1 && after.distinct().size == 1 && before.size == after.size -> {
                            Text("${before.first().displayText()} → ${after.first().displayText()}", color = MaterialTheme.colorScheme.primary)
                        }
                        else -> after.forEachIndexed { index, target ->
                            val old = before.getOrNull(index)
                            Text("Set ${index + 1}: " + when {
                                old == target -> "${target.displayText()} · No change"
                                old == null -> target.displayText()
                                else -> "${old.displayText()} → ${target.displayText()}"
                            })
                        }
                    }
                }
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
private fun TargetList(targets: List<CompletionTarget>) {
    if (targets.distinct().size == 1) Text(targets.first().displayText())
    else targets.forEachIndexed { index, target -> Text("Set ${index + 1}: ${target.displayText()}") }
}

private fun CompletionTarget.displayText() = when (trackingMode) {
    com.jupiman.workouttracker.data.local.entity.TrackingMode.WEIGHT_REPS -> "${formatCentiKg(weightCentiKg)} kg × $reps"
    com.jupiman.workouttracker.data.local.entity.TrackingMode.REPS -> "$reps reps"
    com.jupiman.workouttracker.data.local.entity.TrackingMode.DURATION -> "${durationSeconds ?: 0} sec"
}
