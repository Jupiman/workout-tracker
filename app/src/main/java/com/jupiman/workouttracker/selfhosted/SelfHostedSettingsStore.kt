package com.jupiman.workouttracker.selfhosted

import com.jupiman.workouttracker.preferences.AppPreferencesRepository

data class StoredSelfHostedSettings(
    val enabled: Boolean,
    val serverAddress: String,
    val useHttps: Boolean,
    val lastSuccessfulSyncAt: Long?,
    val lastError: String?,
)

interface SelfHostedSettingsStore {
    suspend fun current(): StoredSelfHostedSettings
    suspend fun setEnabled(value: Boolean)
    suspend fun setServerConfiguration(serverAddress: String, useHttps: Boolean)
    suspend fun setLastSuccessfulSyncAt(value: Long?)
    suspend fun setLastError(value: String?)
}

class DataStoreSelfHostedSettingsStore(
    private val repository: AppPreferencesRepository,
) : SelfHostedSettingsStore {
    override suspend fun current() = repository.current().let {
        StoredSelfHostedSettings(
            enabled = it.selfHostedSyncEnabled,
            serverAddress = it.selfHostedServerAddress,
            useHttps = it.selfHostedUseHttps,
            lastSuccessfulSyncAt = it.selfHostedLastSuccessfulSyncAt,
            lastError = it.selfHostedLastError,
        )
    }
    override suspend fun setEnabled(value: Boolean) { repository.setSelfHostedSyncEnabled(value) }
    override suspend fun setServerConfiguration(serverAddress: String, useHttps: Boolean) {
        repository.setSelfHostedServerConfiguration(serverAddress, useHttps)
    }
    override suspend fun setLastSuccessfulSyncAt(value: Long?) { repository.setSelfHostedLastSuccessfulSyncAt(value) }
    override suspend fun setLastError(value: String?) { repository.setSelfHostedLastError(value) }
}
