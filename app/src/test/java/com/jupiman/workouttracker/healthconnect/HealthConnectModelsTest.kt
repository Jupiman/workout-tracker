package com.jupiman.workouttracker.healthconnect

import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthConnectModelsTest {
    @Test
    fun completedWorkoutMapsSnapshotNamesAndExactTimestamps() {
        val record = session().toHealthConnectWorkoutRecord()!!

        assertEquals(1_000L, record.startTimeMillis)
        assertEquals(61_000L, record.endTimeMillis)
        assertEquals("Day A", record.title)
        assertEquals("Current Program", record.notes)
        assertEquals(0L, record.clientRecordVersion)
        assertEquals(HealthConnectExerciseType.STRENGTH_TRAINING, record.exerciseType)
    }

    @Test
    fun partialWorkoutIsExportedWithoutChangingItsTitle() {
        val record = session(status = WorkoutSessionStatus.PARTIAL).toHealthConnectWorkoutRecord()!!

        assertEquals("Day A", record.title)
        assertEquals("Current Program · Partial workout", record.notes)
    }

    @Test
    fun activeAndInvalidFinalSessionsAreNotExported() {
        assertNull(session(status = WorkoutSessionStatus.ACTIVE, completedAt = null).toHealthConnectWorkoutRecord())
        assertNull(session(completedAt = null).toHealthConnectWorkoutRecord())
        assertNull(session(completedAt = 1_000L).toHealthConnectWorkoutRecord())
    }

    @Test
    fun deterministicIdentityIsStableAndResistsReusedRoomIds() {
        val original = session()
        val restored = original.copy()
        val reusedId = original.copy(startedAt = 2_000L, completedAt = 62_000L)

        assertEquals(
            original.toHealthConnectWorkoutRecord()!!.clientRecordId,
            restored.toHealthConnectWorkoutRecord()!!.clientRecordId,
        )
        assertNotEquals(
            original.toHealthConnectWorkoutRecord()!!.clientRecordId,
            reusedId.toHealthConnectWorkoutRecord()!!.clientRecordId,
        )
    }

    private fun session(
        status: WorkoutSessionStatus = WorkoutSessionStatus.COMPLETED,
        completedAt: Long? = 61_000L,
    ) = WorkoutSessionEntity(
        id = 7,
        sourceWorkoutTemplateId = 2,
        sourceProgramId = 1,
        programNameSnapshot = "Current Program",
        workoutNameSnapshot = "Day A",
        startedAt = 1_000L,
        completedAt = completedAt,
        status = status,
        progressionApplied = status != WorkoutSessionStatus.ACTIVE,
    )
}
