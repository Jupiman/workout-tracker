package com.jupiman.workouttracker.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
        .map { values -> values.toAppPreferences() }

    suspend fun current(): AppPreferences = preferences.first()

    override suspend fun durationPrepSeconds(): Int = current().durationPrepSeconds

    suspend fun setWeightUnit(value: WeightUnit) = dataStore.edit { it[WEIGHT_UNIT] = value.name }
    suspend fun setThemeMode(value: ThemeMode) = dataStore.edit { it[THEME_MODE] = value.name }
    suspend fun setDurationPrepSeconds(value: Int) {
        require(value in ALLOWED_DURATION_PREP_SECONDS) { "Duration preparation must be off, 3, or 5 seconds." }
        dataStore.edit { it[DURATION_PREP_SECONDS] = value }
    }
    suspend fun setKeepPhoneScreenAwake(value: Boolean) = dataStore.edit { it[KEEP_PHONE_SCREEN_AWAKE] = value }
    suspend fun setRestCompletionPhoneAlert(value: Boolean) = dataStore.edit { it[REST_COMPLETION_PHONE_ALERT] = value }
    suspend fun setDurationCompletionPhoneAlert(value: Boolean) = dataStore.edit { it[DURATION_COMPLETION_PHONE_ALERT] = value }
    suspend fun setHealthConnectSyncEnabled(value: Boolean) = dataStore.edit { it[HEALTH_CONNECT_SYNC_ENABLED] = value }
    suspend fun setSelfHostedSyncEnabled(value: Boolean) = dataStore.edit { it[SELF_HOSTED_SYNC_ENABLED] = value }
    suspend fun setSelfHostedServerUrl(value: String) = dataStore.edit { it[SELF_HOSTED_SERVER_URL] = value }
    suspend fun setSelfHostedLastSuccessfulSyncAt(value: Long?) = dataStore.edit {
        if (value == null) it.remove(SELF_HOSTED_LAST_SUCCESS_AT) else it[SELF_HOSTED_LAST_SUCCESS_AT] = value
    }
    suspend fun setSelfHostedLastError(value: String?) = dataStore.edit {
        if (value == null) it.remove(SELF_HOSTED_LAST_ERROR) else it[SELF_HOSTED_LAST_ERROR] = value
    }

    companion object {
        val ALLOWED_DURATION_PREP_SECONDS = setOf(0, 3, 5)

        internal val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        internal val THEME_MODE = stringPreferencesKey("theme_mode")
        internal val DURATION_PREP_SECONDS = intPreferencesKey("duration_prep_seconds")
        internal val KEEP_PHONE_SCREEN_AWAKE = booleanPreferencesKey("keep_phone_screen_awake")
        internal val REST_COMPLETION_PHONE_ALERT = booleanPreferencesKey("rest_completion_phone_alert")
        internal val DURATION_COMPLETION_PHONE_ALERT = booleanPreferencesKey("duration_completion_phone_alert")
        internal val HEALTH_CONNECT_SYNC_ENABLED = booleanPreferencesKey("health_connect_sync_enabled")
        internal val SELF_HOSTED_SYNC_ENABLED = booleanPreferencesKey("self_hosted_sync_enabled")
        internal val SELF_HOSTED_SERVER_URL = stringPreferencesKey("self_hosted_server_url")
        internal val SELF_HOSTED_LAST_SUCCESS_AT = longPreferencesKey("self_hosted_last_success_at")
        internal val SELF_HOSTED_LAST_ERROR = stringPreferencesKey("self_hosted_last_error")

        fun create(context: Context): AppPreferencesRepository = AppPreferencesRepository(
            PreferenceDataStoreFactory.create {
                context.applicationContext.preferencesDataStoreFile("app_preferences")
            },
        )
    }
}

internal fun Preferences.toAppPreferences(): AppPreferences = AppPreferences(
    weightUnit = this[AppPreferencesRepository.WEIGHT_UNIT].toEnumOrDefault(WeightUnit.KG),
    themeMode = this[AppPreferencesRepository.THEME_MODE].toEnumOrDefault(ThemeMode.SYSTEM),
    durationPrepSeconds = this[AppPreferencesRepository.DURATION_PREP_SECONDS]
        ?.takeIf { it in AppPreferencesRepository.ALLOWED_DURATION_PREP_SECONDS }
        ?: 3,
    keepPhoneScreenAwake = this[AppPreferencesRepository.KEEP_PHONE_SCREEN_AWAKE] ?: false,
    restCompletionPhoneAlert = this[AppPreferencesRepository.REST_COMPLETION_PHONE_ALERT] ?: true,
    durationCompletionPhoneAlert = this[AppPreferencesRepository.DURATION_COMPLETION_PHONE_ALERT] ?: true,
    healthConnectSyncEnabled = this[AppPreferencesRepository.HEALTH_CONNECT_SYNC_ENABLED] ?: false,
    selfHostedSyncEnabled = this[AppPreferencesRepository.SELF_HOSTED_SYNC_ENABLED] ?: false,
    selfHostedServerUrl = this[AppPreferencesRepository.SELF_HOSTED_SERVER_URL] ?: "",
    selfHostedLastSuccessfulSyncAt = this[AppPreferencesRepository.SELF_HOSTED_LAST_SUCCESS_AT],
    selfHostedLastError = this[AppPreferencesRepository.SELF_HOSTED_LAST_ERROR],
)

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(default: T): T =
    this?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default
