package com.jupiman.workouttracker.healthconnect

import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectSyncManagerTest {
    @Test
    fun manualSyncUsesAllAndOnlyValidFinalizedSessions() = runTest {
        val gateway = FakeHealthConnectGateway()
        val manager = manager(
            gateway = gateway,
            sessions = listOf(session(1), session(2, WorkoutSessionStatus.PARTIAL), session(3, WorkoutSessionStatus.ACTIVE)),
        )

        assertEquals(HealthConnectSyncResult.Completed(2), manager.syncAllFinalizedWorkouts())
        assertEquals(setOf("Day 1", "Day 2"), gateway.records.values.map { it.title }.toSet())
    }

    @Test
    fun repeatSyncUsesTheSameExternalIdentity() = runTest {
        val gateway = FakeHealthConnectGateway()
        val manager = manager(gateway, listOf(session(1)))

        manager.syncAllFinalizedWorkouts()
        manager.syncAllFinalizedWorkouts()

        assertEquals(2, gateway.writeCalls)
        assertEquals(1, gateway.records.size)
    }

    @Test
    fun missingOrRevokedPermissionStopsWritesAndReportsPermissionRequired() = runTest {
        val gateway = FakeHealthConnectGateway(hasPermission = false)
        val manager = manager(gateway, listOf(session(1)))

        assertEquals(HealthConnectSyncResult.PermissionRequired, manager.syncAllFinalizedWorkouts())
        assertFalse(manager.settingsState().hasWritePermission)
        assertEquals(0, gateway.writeCalls)
    }

    @Test
    fun unavailableAndUpdateRequiredProvidersStopWithoutWriting() = runTest {
        listOf(
            HealthConnectAvailability.UNAVAILABLE to HealthConnectSyncResult.Unavailable,
            HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED to HealthConnectSyncResult.ProviderUpdateRequired,
        ).forEach { (availability, expected) ->
            val gateway = FakeHealthConnectGateway(availability = availability)
            assertEquals(expected, manager(gateway, listOf(session(1))).syncAllFinalizedWorkouts())
            assertEquals(0, gateway.writeCalls)
        }
    }

    @Test
    fun automaticSyncHonorsTheDeviceLocalPreference() = runTest {
        val disabledGateway = FakeHealthConnectGateway()
        assertEquals(
            HealthConnectSyncResult.Disabled,
            manager(disabledGateway, listOf(session(1)), enabled = false).syncIfEnabled(),
        )
        assertEquals(0, disabledGateway.writeCalls)

        val enabledGateway = FakeHealthConnectGateway()
        assertEquals(
            HealthConnectSyncResult.Completed(1),
            manager(enabledGateway, listOf(session(1)), enabled = true).syncIfEnabled(),
        )
        assertEquals(1, enabledGateway.writeCalls)
    }

    @Test
    fun gatewayFailureIsAContainedExternalSyncFailure() = runTest {
        val gateway = FakeHealthConnectGateway(failWrites = true)

        assertEquals(HealthConnectSyncResult.Failed, manager(gateway, listOf(session(1))).syncAllFinalizedWorkouts())
        assertTrue(gateway.records.isEmpty())
    }

    private fun manager(
        gateway: FakeHealthConnectGateway,
        sessions: List<WorkoutSessionEntity>,
        enabled: Boolean = true,
    ) = HealthConnectSyncManager(
        gateway = gateway,
        workoutSource = FinalizedWorkoutSource { sessions },
        isSyncEnabled = { enabled },
    )

    private fun session(id: Long, status: WorkoutSessionStatus = WorkoutSessionStatus.COMPLETED) =
        WorkoutSessionEntity(
            id = id,
            sourceWorkoutTemplateId = id,
            sourceProgramId = 1,
            programNameSnapshot = "Program snapshot",
            workoutNameSnapshot = "Day $id",
            startedAt = id * 10_000L,
            completedAt = id * 10_000L + 5_000L,
            status = status,
            progressionApplied = status != WorkoutSessionStatus.ACTIVE,
        )

    private class FakeHealthConnectGateway(
        var availability: HealthConnectAvailability = HealthConnectAvailability.AVAILABLE,
        var hasPermission: Boolean = true,
        var failWrites: Boolean = false,
    ) : HealthConnectGateway {
        var writeCalls = 0
        val records = linkedMapOf<String, HealthConnectWorkoutRecord>()

        override suspend fun availability() = availability
        override suspend fun hasWriteExercisePermission() = hasPermission
        override suspend fun writeExerciseSessions(records: List<HealthConnectWorkoutRecord>) {
            writeCalls += 1
            if (failWrites) error("Provider failure")
            records.forEach { record -> this.records[record.clientRecordId] = record }
        }
    }
}
