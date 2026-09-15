package com.jupiman.workouttracker.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AppPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : DurationPreparationProvider {
    val preferences: Flow<AppPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map(::mapPreferences)

    suspend fun current(): AppPreferences = preferences.first()

    override suspend fun durationPrepSeconds(): Int = current().durationPrepSeconds

    suspend fun setPreferences(value: AppPreferences) = dataStore.edit {
        it[WEIGHT_UNIT] = value.weightUnit.name
        it[THEME_MODE] = value.themeMode.name
        it[DURATION_PREP_SECONDS] = value.durationPrepSeconds
        it[KEEP_PHONE_SCREEN_AWAKE] = value.keepPhoneScreenAwake
        it[REST_COMPLETION_PHONE_ALERT] = value.restCompletionPhoneAlert
        it[DURATION_COMPLETION_PHONE_ALERT] = value.durationCompletionPhoneAlert
    }

    suspend fun setWeightUnit(value: WeightUnit) = dataStore.edit { it[WEIGHT_UNIT] = value.name }
    suspend fun setThemeMode(value: ThemeMode) = dataStore.edit { it[THEME_MODE] = value.name }
    suspend fun setDurationPrepSeconds(value: Int) {
        require(value in ALLOWED_DURATION_PREP_SECONDS) { "Duration preparation must be off, 3, or 5 seconds." }
        dataStore.edit { it[DURATION_PREP_SECONDS] = value }
    }
    suspend fun setKeepPhoneScreenAwake(value: Boolean) = dataStore.edit { it[KEEP_PHONE_SCREEN_AWAKE] = value }
    suspend fun setRestCompletionPhoneAlert(value: Boolean) = dataStore.edit { it[REST_COMPLETION_PHONE_ALERT] = value }
    suspend fun setDurationCompletionPhoneAlert(value: Boolean) = dataStore.edit { it[DURATION_COMPLETION_PHONE_ALERT] = value }

    private fun mapPreferences(values: Preferences): AppPreferences = AppPreferences(
        weightUnit = values[WEIGHT_UNIT].toEnumOrDefault(WeightUnit.KG),
        themeMode = values[THEME_MODE].toEnumOrDefault(ThemeMode.SYSTEM),
        durationPrepSeconds = values[DURATION_PREP_SECONDS]
            ?.takeIf { it in ALLOWED_DURATION_PREP_SECONDS }
            ?: 3,
        keepPhoneScreenAwake = values[KEEP_PHONE_SCREEN_AWAKE] ?: false,
        restCompletionPhoneAlert = values[REST_COMPLETION_PHONE_ALERT] ?: true,
        durationCompletionPhoneAlert = values[DURATION_COMPLETION_PHONE_ALERT] ?: true,
    )

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
        this?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default

    companion object {
        val ALLOWED_DURATION_PREP_SECONDS = setOf(0, 3, 5)

        internal val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        internal val THEME_MODE = stringPreferencesKey("theme_mode")
        internal val DURATION_PREP_SECONDS = intPreferencesKey("duration_prep_seconds")
        internal val KEEP_PHONE_SCREEN_AWAKE = booleanPreferencesKey("keep_phone_screen_awake")
        internal val REST_COMPLETION_PHONE_ALERT = booleanPreferencesKey("rest_completion_phone_alert")
        internal val DURATION_COMPLETION_PHONE_ALERT = booleanPreferencesKey("duration_completion_phone_alert")

        fun create(context: Context): AppPreferencesRepository = AppPreferencesRepository(
            PreferenceDataStoreFactory.create {
                context.applicationContext.preferencesDataStoreFile("app_preferences")
            },
        )
    }
}
