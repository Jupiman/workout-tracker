package com.jupiman.workouttracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jupiman.workouttracker.navigation.WorkoutTrackerApp
import com.jupiman.workouttracker.ui.theme.WorkoutTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        val container = (application as WorkoutTrackerApplication).container

        setContent {
            WorkoutTrackerTheme {
                WorkoutTrackerApp(container = container)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) return

        ActivityCompat.requestPermissions(this, arrayOf(permission), NOTIFICATION_PERMISSION_REQUEST_CODE)
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 4001
    }
}
