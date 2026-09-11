package com.jupiman.workouttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jupiman.workouttracker.navigation.WorkoutTrackerApp
import com.jupiman.workouttracker.ui.theme.WorkoutTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as WorkoutTrackerApplication).container

        setContent {
            WorkoutTrackerTheme {
                WorkoutTrackerApp(container = container)
            }
        }
    }
}

