package com.jupiman.workouttracker.selfhosted

import com.jupiman.workouttracker.preferences.AppPreferencesRepository

data class StoredSelfHostedSettings(
    val enabled: Boolean,
    val serverUrl: String,
    val lastSuccessfulSyncAt: Long?,
    val lastError: String?,
)

interface SelfHostedSettingsStore {
    suspend fun current(): StoredSelfHostedSettings
    suspend fun setEnabled(value: Boolean)
    suspend fun setServerUrl(value: String)
    suspend fun setLastSuccessfulSyncAt(value: Long?)
    suspend fun setLastError(value: String?)
}

class DataStoreSelfHostedSettingsStore(
    private val repository: AppPreferencesRepository,
) : SelfHostedSettingsStore {
    override suspend fun current() = repository.current().let {
        StoredSelfHostedSettings(
            enabled = it.selfHostedSyncEnabled,
            serverUrl = it.selfHostedServerUrl,
            lastSuccessfulSyncAt = it.selfHostedLastSuccessfulSyncAt,
            lastError = it.selfHostedLastError,
        )
    }
    override suspend fun setEnabled(value: Boolean) { repository.setSelfHostedSyncEnabled(value) }
    override suspend fun setServerUrl(value: String) { repository.setSelfHostedServerUrl(value) }
    override suspend fun setLastSuccessfulSyncAt(value: Long?) { repository.setSelfHostedLastSuccessfulSyncAt(value) }
    override suspend fun setLastError(value: String?) { repository.setSelfHostedLastError(value) }
}
