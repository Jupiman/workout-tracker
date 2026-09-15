package com.jupiman.workouttracker.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.jupiman.workouttracker.preferences.AppPreferences

val LocalAppPreferences = staticCompositionLocalOf { AppPreferences() }
