package com.jupiman.workouttracker.healthconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.jupiman.workouttracker.ui.theme.WorkoutSpacing
import com.jupiman.workouttracker.ui.theme.WorkoutTrackerTheme

class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkoutTrackerTheme {
                Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(padding).padding(WorkoutSpacing.screen),
                        verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.section),
                    ) {
                        Text("Health Connect", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Workout Companion uses Health Connect only to write workout sessions that you record in Workout Companion.",
                        )
                        Text(
                            "It writes the workout start time, end time, strength-training type, workout name, and program name.",
                        )
                        Text("Workout Companion does not read health data from Health Connect.")
                        Text("Workout Companion does not upload Health Connect data to its own server.")
                        Text(
                            "You can revoke Health Connect permission at any time in Android settings.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
