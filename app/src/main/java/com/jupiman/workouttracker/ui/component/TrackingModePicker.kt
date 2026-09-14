package com.jupiman.workouttracker.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.jupiman.workouttracker.data.local.entity.TrackingMode

@Composable
fun TrackingModePicker(mode: TrackingMode, onChange: (TrackingMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }) { Text("Tracking: ${mode.label()}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TrackingMode.entries.forEach { entry ->
                DropdownMenuItem(text = { Text(entry.label()) }, onClick = { expanded = false; onChange(entry) })
            }
        }
    }
}

private fun TrackingMode.label() = when (this) {
    TrackingMode.WEIGHT_REPS -> "Weight + reps"
    TrackingMode.REPS -> "Reps"
    TrackingMode.DURATION -> "Duration"
}
