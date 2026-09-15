package com.jupiman.workouttracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiman.workouttracker.navigation.WorkoutTrackerApp
import com.jupiman.workouttracker.preferences.AppPreferences
import com.jupiman.workouttracker.preferences.shouldUseDarkTheme
import com.jupiman.workouttracker.preferences.shouldKeepPhoneScreenAwake
import com.jupiman.workouttracker.ui.LocalAppPreferences
import com.jupiman.workouttracker.ui.theme.WorkoutTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        val container = (application as WorkoutTrackerApplication).container

        setContent {
            val preferences by container.appPreferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = AppPreferences(),
            )
            val activeSession by container.workoutSessionRepository.activeSession.collectAsStateWithLifecycle(
                initialValue = null,
            )
            val darkTheme = shouldUseDarkTheme(preferences.themeMode, isSystemInDarkTheme())
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            DisposableEffect(preferences.keepPhoneScreenAwake, activeSession?.id) {
                val keepAwake = shouldKeepPhoneScreenAwake(preferences, activeSession != null)
                window.decorView.keepScreenOn = keepAwake
                onDispose { window.decorView.keepScreenOn = false }
            }
            WorkoutTrackerTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalAppPreferences provides preferences) {
                    WorkoutTrackerApp(container = container)
                }
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
