package com.jupiman.workouttracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import kotlinx.coroutines.launch
import com.jupiman.workouttracker.data.local.entity.TrackingMode

@Composable
internal fun AddSessionExerciseDialog(
    sessionId: Long,
    exercises: List<ExerciseEntity>,
    onDismiss: () -> Unit,
    onAdd: suspend (Long, Long, String, String, String, String, TrackingMode, String) -> Unit,
) {
    var selectedId by rememberSaveable(sessionId) { mutableStateOf<Long?>(null) }
    var name by rememberSaveable(sessionId) { mutableStateOf("") }
    var sets by rememberSaveable(sessionId) { mutableStateOf("3") }
    var reps by rememberSaveable(sessionId) { mutableStateOf("8") }
    var weight by rememberSaveable(sessionId) { mutableStateOf("0") }
    var trackingMode by rememberSaveable(sessionId) { mutableStateOf(TrackingMode.WEIGHT_REPS) }
    var durationSeconds by rememberSaveable(sessionId) { mutableStateOf("60") }
    var rest by rememberSaveable(sessionId) { mutableStateOf("180") }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val selected = exercises.firstOrNull { it.id == selectedId && it.name == name && !it.archived }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Add exercise for today") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Search and select an exercise from your library. These sets are for today only.")
                AddExerciseDialogContent(
                    exercises = exercises,
                    selectedExerciseId = selectedId,
                    onSelectedExerciseChange = { id ->
                        selectedId = id
                        name = exercises.firstOrNull { it.id == id }?.name.orEmpty()
                    },
                    exerciseName = name,
                    onExerciseNameChange = { name = it; selectedId = null },
                    sets = sets, onSetsChange = { sets = it },
                    currentTargetReps = reps, onCurrentTargetRepsChange = { reps = it },
                    currentWeight = weight, onCurrentWeightChange = { weight = it },
                    restSeconds = rest, onRestSecondsChange = { rest = it },
                    repMin = reps, onRepMinChange = {},
                    repMax = reps, onRepMaxChange = {},
                    increment = "2.5", onIncrementChange = {},
                    sessionOnly = true,
                    trackingMode = trackingMode, onTrackingModeChange = { trackingMode = it },
                    durationSeconds = durationSeconds, onDurationSecondsChange = { durationSeconds = it },
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = selected != null && !saving, onClick = {
                val id = selected?.id ?: return@Button
                saving = true
                scope.launch {
                    try {
                        onAdd(sessionId, id, sets, reps, weight, rest, trackingMode, durationSeconds)
                        onDismiss()
                    } catch (exception: IllegalArgumentException) {
                        error = exception.message
                    } catch (exception: IllegalStateException) {
                        error = exception.message
                    } finally {
                        saving = false
                    }
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = onDismiss) { Text("Cancel") } },
    )
}
