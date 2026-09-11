package com.jupiman.workouttracker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiman.workouttracker.ui.viewmodel.HomeViewModel

@Composable
fun WorkoutHomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Workout",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.activeSession != null) {
            Text(
                text = "Active workout",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Resume workout")
            }
        } else {
            Text(
                text = "Active program",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = uiState.activeProgram?.name ?: "No active program yet",
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Next workout",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = uiState.nextWorkoutName ?: "No workout templates yet",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {},
                enabled = uiState.nextWorkoutName != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Start workout")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {},
                enabled = uiState.activeProgram != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Choose another workout")
            }
        }
    }
}
