package com.jupiman.workouttracker.selfhosted

import com.jupiman.workouttracker.data.local.entity.SelfHostedSyncState
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfHostedSyncManagerTest {
    @Test
    fun automaticSyncUploadsOnlyPendingWhileFullSyncRetriesEveryFinalizedWorkout() = runTest {
        val store = FakeWorkoutStore(
            listOf(
                workout(1, SelfHostedSyncState.PENDING),
                workout(2, SelfHostedSyncState.SYNCED),
                workout(3, SelfHostedSyncState.FAILED_PERMANENT),
                workout(4, SelfHostedSyncState.PENDING, WorkoutSessionStatus.ACTIVE),
            ),
        )
        val client = FakeClient()
        val manager = manager(store, client)

        assertEquals(1, manager.syncPending().synchronized)
        assertEquals(listOf(listOf(syncId(1))), client.uploadedBatches)

        client.uploadedBatches.clear()
        assertEquals(3, manager.syncAll().synchronized)
        assertEquals(3, manager.syncAll().synchronized)
        assertEquals(
            listOf(listOf(syncId(1), syncId(2), syncId(3)), listOf(syncId(1), syncId(2), syncId(3))),
            client.uploadedBatches,
        )
        assertTrue((1L..3L).all { store.states[it] == SelfHostedSyncState.SYNCED })
        assertEquals(SelfHostedSyncState.PENDING, store.states.getValue(4))
    }

    @Test
    fun processedBatchKeepsSuccessAndMarksOnlyRejectedWorkoutPermanent() = runTest {
        val store = FakeWorkoutStore(listOf(workout(1), workout(2)))
        val client = FakeClient().apply {
            nextUpload = UploadResult.Processed(
                listOf(
                    RecordUploadResult(syncId(1), true),
                    RecordUploadResult(syncId(2), false, "INVALID_PAYLOAD", "Bad workout"),
                ),
            )
        }

        val manager = manager(store, client)
        val summary = manager.syncPending()

        assertEquals(1, summary.synchronized)
        assertEquals(1, summary.failed)
        assertFalse(summary.retryNeeded)
        assertEquals(SelfHostedSyncState.SYNCED, store.states.getValue(1))
        assertEquals(SelfHostedSyncState.FAILED_PERMANENT, store.states.getValue(2))
        assertEquals("1 workout could not be synchronized.", manager.settingsState().latestError)
    }

    @Test
    fun retryableFailureRemainsPendingButAuthenticationStopsWithoutRetryLoop() = runTest {
        val store = FakeWorkoutStore(listOf(workout(1)))
        val client = FakeClient().apply { nextUpload = UploadResult.Retryable("offline") }
        val manager = manager(store, client)

        val offline = manager.syncPending()
        assertTrue(offline.retryNeeded)
        assertEquals(SelfHostedSyncState.PENDING, store.states.getValue(1))
        assertEquals("offline", store.errors[1])

        client.connection = ConnectionTestResult.Unauthorized
        val unauthorized = manager.syncPending()
        assertFalse(unauthorized.retryNeeded)
        assertEquals("Authentication failed.", (manager.settingsState().latestError))
    }

    @Test
    fun serverInfoUnavailableRequestsWorkManagerRetryBeforeUploading() = runTest {
        val store = FakeWorkoutStore(listOf(workout(1)))
        val client = FakeClient().apply { connection = ConnectionTestResult.Unreachable("HTTP 503") }

        val summary = manager(store, client).syncPending()

        assertTrue(summary.retryNeeded)
        assertTrue(client.uploadedBatches.isEmpty())
        assertEquals(SelfHostedSyncState.PENDING, store.states.getValue(1))
    }

    @Test
    fun disabledAutomaticSyncMakesNoClientCalls() = runTest {
        val store = FakeWorkoutStore(listOf(workout(1)))
        val client = FakeClient()
        val settings = FakeSettingsStore(enabled = false)
        val manager = manager(store, client, settings)

        assertEquals(SelfHostedSyncSummary(0, 0, false), manager.syncPending())
        assertEquals(SelfHostedSyncSummary(0, 0, false), manager.syncAllIfEnabled())
        assertEquals(0, client.connectionCalls)
        assertTrue(client.uploadedBatches.isEmpty())
    }

    @Test
    fun enablingValidConfigurationSchedulesFullCatchUp() = runTest {
        val scheduler = FakeScheduler()
        val settings = FakeSettingsStore(enabled = false)
        val manager = SelfHostedSyncManager(
            client = FakeClient(),
            workoutStore = FakeWorkoutStore(emptyList()),
            settingsStore = settings,
            tokenStore = FakeTokenStore(),
            scheduler = scheduler,
        )

        assertTrue(manager.setEnabled(true).isSuccess)

        assertTrue(settings.current().enabled)
        assertEquals(1, scheduler.fullSchedules)
    }

    @Test
    fun fullSyncUsesBoundedBatchesOfFifty() = runTest {
        val store = FakeWorkoutStore((1L..51L).map { workout(it, SelfHostedSyncState.SYNCED) })
        val client = FakeClient()

        val summary = manager(store, client).syncAll()

        assertEquals(51, summary.synchronized)
        assertEquals(listOf(50, 1), client.uploadedBatches.map { it.size })
    }

    @Test
    fun pastedUrlUpdatesProtocolAndEverySyncUsesItsEffectiveBaseUrl() = runTest {
        val settings = FakeSettingsStore(enabled = false)
        val client = FakeClient()
        val manager = manager(FakeWorkoutStore(listOf(workout(1))), client, settings)

        assertTrue(manager.saveConfiguration("http://192.168.1.140:5544/", true, null).isSuccess)
        assertEquals("192.168.1.140:5544", settings.current().serverAddress)
        assertFalse(settings.current().useHttps)
        assertEquals(ConnectionTestResult.Success("1.0.0"), manager.testConnection("192.168.1.140:5544", false, null))
        manager.syncAll()

        assertTrue(client.configurations.isNotEmpty())
        assertTrue(client.configurations.all { it.baseUrl == "http://192.168.1.140:5544" })
    }

    private fun manager(
        store: FakeWorkoutStore,
        client: FakeClient,
        settings: FakeSettingsStore = FakeSettingsStore(),
    ) = SelfHostedSyncManager(
        client = client,
        workoutStore = store,
        settingsStore = settings,
        tokenStore = FakeTokenStore(),
        scheduler = NoOpSelfHostedWorkoutScheduler,
    )

    private fun workout(
        id: Long,
        state: SelfHostedSyncState = SelfHostedSyncState.PENDING,
        status: WorkoutSessionStatus = WorkoutSessionStatus.COMPLETED,
    ) =
        WorkoutSessionWithDetails(
            WorkoutSessionEntity(
                id = id,
                sourceWorkoutTemplateId = 1,
                sourceProgramId = 1,
                programNameSnapshot = "Program",
                workoutNameSnapshot = "Day $id",
                startedAt = 1_000,
                completedAt = if (status == WorkoutSessionStatus.ACTIVE) null else 2_000,
                status = status,
                progressionApplied = status != WorkoutSessionStatus.ACTIVE,
                syncId = syncId(id),
                selfHostedSyncState = state,
            ),
            emptyList(),
        )

    private fun syncId(id: Long) = "00000000-0000-4000-8000-${id.toString().padStart(12, '0')}"

    private class FakeWorkoutStore(workouts: List<WorkoutSessionWithDetails>) : SelfHostedWorkoutStore {
        private val workouts = workouts.sortedBy { it.session.completedAt }
        val states = workouts.associate { it.session.id to it.session.selfHostedSyncState }.toMutableMap()
        val errors = mutableMapOf<Long, String?>()

        override suspend fun pending(limit: Int) = workouts.filter {
            it.session.status != WorkoutSessionStatus.ACTIVE && states[it.session.id] == SelfHostedSyncState.PENDING
        }.take(limit)
        override suspend fun finalized(limit: Int, offset: Int) = workouts.filter {
            it.session.status != WorkoutSessionStatus.ACTIVE
        }.drop(offset).take(limit)
        override suspend fun pendingCount() = states.values.count { it == SelfHostedSyncState.PENDING }
        override suspend fun permanentFailureCount() = states.values.count { it == SelfHostedSyncState.FAILED_PERMANENT }
        override suspend fun updateState(id: Long, state: SelfHostedSyncState, attemptAt: Long?, error: String?) {
            states[id] = state
            errors[id] = error
        }
    }

    private class FakeSettingsStore(
        enabled: Boolean = true,
    ) : SelfHostedSettingsStore {
        private var value = StoredSelfHostedSettings(enabled, "example.com", true, null, null)
        override suspend fun current() = value
        override suspend fun setEnabled(value: Boolean) { this.value = this.value.copy(enabled = value) }
        override suspend fun setServerConfiguration(serverAddress: String, useHttps: Boolean) {
            value = value.copy(serverAddress = serverAddress, useHttps = useHttps)
        }
        override suspend fun setLastSuccessfulSyncAt(value: Long?) { this.value = this.value.copy(lastSuccessfulSyncAt = value) }
        override suspend fun setLastError(value: String?) { this.value = this.value.copy(lastError = value) }
    }

    private class FakeTokenStore : SelfHostedTokenStore {
        private var value: String? = "token"
        override fun hasToken() = value != null
        override fun getToken() = value
        override fun setToken(token: String) { value = token }
        override fun clearToken() { value = null }
    }

    private class FakeClient : SelfHostedSyncClient {
        var connection: ConnectionTestResult = ConnectionTestResult.Success("1.0.0")
        var nextUpload: UploadResult? = null
        var connectionCalls = 0
        val uploadedBatches = mutableListOf<List<String>>()
        val configurations = mutableListOf<SelfHostedConfiguration>()
        override suspend fun testConnection(configuration: SelfHostedConfiguration): ConnectionTestResult {
            connectionCalls++
            configurations += configuration
            return connection
        }
        override suspend fun uploadWorkout(configuration: SelfHostedConfiguration, workout: SelfHostedWorkoutPayload) =
            nextUpload ?: UploadResult.Processed(listOf(RecordUploadResult(workout.syncId, true)))
        override suspend fun uploadBatch(
            configuration: SelfHostedConfiguration,
            workouts: List<SelfHostedWorkoutPayload>,
        ): UploadResult {
            configurations += configuration
            uploadedBatches += workouts.map { it.syncId }
            return nextUpload ?: UploadResult.Processed(workouts.map { RecordUploadResult(it.syncId, true) })
        }
    }

    private class FakeScheduler : SelfHostedWorkoutScheduler {
        var fullSchedules = 0
        override fun schedulePending() = Unit
        override fun scheduleFullSync() { fullSchedules++ }
        override fun cancel() = Unit
    }
}
