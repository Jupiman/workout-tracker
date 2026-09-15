package com.jupiman.workouttracker.preferences

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun missingPreferencesUseDocumentedDefaults() {
        assertEquals(AppPreferences(), preferencesOf().toAppPreferences())
    }

    @Test
    fun invalidStoredEnumsAndDurationPreparationUseDefaults() {
        val stored = preferencesOf(
            stringPreferencesKey("weight_unit") to "STONE",
            stringPreferencesKey("theme_mode") to "MIDNIGHT",
            intPreferencesKey("duration_prep_seconds") to 9,
            booleanPreferencesKey("keep_phone_screen_awake") to true,
            booleanPreferencesKey("rest_completion_phone_alert") to false,
            booleanPreferencesKey("duration_completion_phone_alert") to false,
        ).toAppPreferences()

        assertEquals(WeightUnit.KG, stored.weightUnit)
        assertEquals(ThemeMode.SYSTEM, stored.themeMode)
        assertEquals(3, stored.durationPrepSeconds)
        assertTrue(stored.keepPhoneScreenAwake)
        assertFalse(stored.restCompletionPhoneAlert)
        assertFalse(stored.durationCompletionPhoneAlert)
    }

    @Test
    fun themeAndScreenAwakePoliciesAreDeterministic() {
        assertFalse(shouldUseDarkTheme(ThemeMode.LIGHT, systemDark = true))
        assertTrue(shouldUseDarkTheme(ThemeMode.DARK, systemDark = false))
        assertTrue(shouldUseDarkTheme(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(shouldKeepPhoneScreenAwake(AppPreferences(keepPhoneScreenAwake = true), hasActiveWorkout = false))
        assertTrue(shouldKeepPhoneScreenAwake(AppPreferences(keepPhoneScreenAwake = true), hasActiveWorkout = true))
    }
}
