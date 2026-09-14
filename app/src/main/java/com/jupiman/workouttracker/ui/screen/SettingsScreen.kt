package com.jupiman.workouttracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.jupiman.workouttracker.ui.theme.WorkoutSpacing
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(versionName: String?, onExportBackup: () -> Unit, onRestoreBackup: () -> Unit) {
    var confirmingRestore by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = WorkoutSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.section),
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }
        item { Text("DATA", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(WorkoutSpacing.card), verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item)) {
                    Text("Backup & restore", style = MaterialTheme.typography.titleLarge)
                    Text("Export or restore a local JSON backup of programs, history, and active workout state.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) { Text("Export backup") }
                    OutlinedButton(onClick = { confirmingRestore = true }, modifier = Modifier.fillMaxWidth()) { Text("Restore backup") }
                }
            }
        }
        item { Text("ABOUT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(WorkoutSpacing.card), verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item)) {
                    Text("Workout Companion", style = MaterialTheme.typography.titleMedium)
                    versionName?.let { Text("Version $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
    if (confirmingRestore) {
        AlertDialog(
            onDismissRequest = { confirmingRestore = false },
            title = { Text("Restore backup?") },
            text = { Text("This replaces the local Workout Companion data on this device with the selected backup file.") },
            confirmButton = {
                Button(onClick = { confirmingRestore = false; onRestoreBackup() }) { Text("Choose backup") }
            },
            dismissButton = { TextButton(onClick = { confirmingRestore = false }) { Text("Cancel") } },
        )
    }
}
