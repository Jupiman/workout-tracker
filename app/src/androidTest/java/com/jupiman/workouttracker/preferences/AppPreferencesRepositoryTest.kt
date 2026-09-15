package com.jupiman.workouttracker.preferences

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesRepositoryTest {
    @Test
    fun settingsPersistWhenTheRepositoryAndDataStoreAreRecreated() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "phase9-preferences-${System.nanoTime()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        try {
            val first = repository(file, scope)
            first.setWeightUnit(WeightUnit.LB)
            first.setThemeMode(ThemeMode.DARK)
            first.setDurationPrepSeconds(5)
            first.setKeepPhoneScreenAwake(true)
            first.setRestCompletionPhoneAlert(false)
            first.setDurationCompletionPhoneAlert(false)
            first.setHealthConnectSyncEnabled(true)

            val firstJob = scope.coroutineContext[Job]!!
            scope.cancel()
            firstJob.join()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            assertEquals(
                AppPreferences(
                    weightUnit = WeightUnit.LB,
                    themeMode = ThemeMode.DARK,
                    durationPrepSeconds = 5,
                    keepPhoneScreenAwake = true,
                    restCompletionPhoneAlert = false,
                    durationCompletionPhoneAlert = false,
                    healthConnectSyncEnabled = true,
                ),
                repository(file, scope).current(),
            )
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun granularConcurrentUpdatesDoNotOverwriteOtherKeys() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "phase9-concurrent-${System.nanoTime()}.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = repository(file, scope)
            coroutineScope {
                listOf(
                    launch { repository.setWeightUnit(WeightUnit.LB) },
                    launch { repository.setThemeMode(ThemeMode.DARK) },
                    launch { repository.setDurationPrepSeconds(0) },
                    launch { repository.setKeepPhoneScreenAwake(true) },
                    launch { repository.setRestCompletionPhoneAlert(false) },
                    launch { repository.setDurationCompletionPhoneAlert(false) },
                    launch { repository.setHealthConnectSyncEnabled(true) },
                ).joinAll()
            }

            assertEquals(
                AppPreferences(WeightUnit.LB, ThemeMode.DARK, 0, true, false, false, true),
                repository.current(),
            )
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    private fun repository(file: File, scope: CoroutineScope) = AppPreferencesRepository(
        PreferenceDataStoreFactory.create(scope = scope) { file },
    )
}
