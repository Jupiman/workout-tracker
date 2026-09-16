package com.jupiman.workouttracker.selfhosted

import com.jupiman.workouttracker.data.local.entity.SessionExerciseEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.TrackingMode
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.model.SessionExerciseWithSets
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfHostedPayloadTest {
    @Test
    fun mapsImmutableSnapshotsWithUtcTimeAndNullsForIrrelevantFields() {
        val payload = workout(
            status = WorkoutSessionStatus.PARTIAL,
            exercises = listOf(
                exercise(1, TrackingMode.WEIGHT_REPS, weight = 7000, reps = 8, duration = null),
                exercise(2, TrackingMode.REPS, weight = 0, reps = 10, duration = null),
                exercise(3, TrackingMode.DURATION, weight = 0, reps = 1, duration = 45),
            ),
        ).toSelfHostedPayload().json

        assertEquals(1, payload.getInt("schemaVersion"))
        assertEquals("1970-01-01T00:00:01Z", payload.getString("startedAt"))
        assertEquals("1970-01-01T00:00:02Z", payload.getString("completedAt"))
        assertEquals("PARTIAL", payload.getString("status"))
        val weightReps = payload.getJSONArray("exercises").getJSONObject(0)
        assertEquals(7000, weightReps.getInt("prescribedWeightCentiKg"))
        assertEquals(7250, weightReps.getInt("resultingProgressionWeightCentiKg"))
        val reps = payload.getJSONArray("exercises").getJSONObject(1)
        assertTrue(reps.isNull("prescribedWeightCentiKg"))
        assertTrue(reps.getJSONArray("sets").getJSONObject(0).isNull("actualWeightCentiKg"))
        val duration = payload.getJSONArray("exercises").getJSONObject(2)
        assertTrue(duration.isNull("repMin"))
        assertTrue(duration.isNull("targetReps"))
        assertEquals(45, duration.getInt("targetDurationSeconds"))
        assertEquals(50, duration.getInt("resultingProgressionDurationSeconds"))
        assertTrue(duration.getJSONArray("sets").getJSONObject(0).isNull("actualReps"))
    }

    @Test
    fun refusesActiveWorkout() {
        assertThrows(IllegalArgumentException::class.java) {
            workout(status = WorkoutSessionStatus.ACTIVE).toSelfHostedPayload()
        }
    }

    @Test
    fun serializesFixedRangeWarmupPerSetAndSessionOnlySetTypes() {
        val base = exercise(1, TrackingMode.WEIGHT_REPS, weight = 7000, reps = 8, duration = null)
        fun set(
            id: Long,
            type: SetType,
            status: SessionSetStatus = SessionSetStatus.COMPLETED,
            prescribedWeight: Int? = 7000,
            prescribedReps: Int? = 8,
            actualReps: Int? = 8,
            planned: Boolean = false,
            progression: Boolean = false,
        ) = base.sets.single().copy(
            id = id,
            syncId = "30000000-0000-4000-8000-${id.toString().padStart(12, '0')}",
            setOrder = id.toInt(),
            setType = type,
            status = status,
            prescribedWeightCentiKg = prescribedWeight,
            prescribedReps = prescribedReps,
            actualWeightCentiKg = if (status == SessionSetStatus.COMPLETED) prescribedWeight else null,
            actualReps = if (status == SessionSetStatus.COMPLETED) actualReps else null,
            isPlanned = planned,
            countsForProgression = progression,
            completedAt = 1_900,
        )
        val details = base.copy(
            exercise = base.exercise.copy(repMinSnapshot = 8, repMaxSnapshot = 8, targetRepsSnapshot = 8),
            sets = listOf(
                set(1, SetType.WARMUP, prescribedWeight = 3500, prescribedReps = 5),
                set(2, SetType.WORKING, prescribedWeight = 6750, planned = true, progression = true),
                set(3, SetType.EXTRA),
                set(4, SetType.AMRAP, prescribedReps = null, actualReps = 12),
                set(5, SetType.DROP, status = SessionSetStatus.SKIPPED, prescribedWeight = 5000),
            ),
        )

        val exercise = workout(WorkoutSessionStatus.COMPLETED, listOf(details))
            .toSelfHostedPayload().json.getJSONArray("exercises").getJSONObject(0)

        assertEquals(8, exercise.getInt("repMin"))
        assertEquals(8, exercise.getInt("repMax"))
        assertTrue(!exercise.has("id"))
        val sets = exercise.getJSONArray("sets")
        assertEquals(listOf("WARMUP", "WORKING", "EXTRA", "AMRAP", "DROP"), List(sets.length()) { sets.getJSONObject(it).getString("setType") })
        assertEquals(6750, sets.getJSONObject(1).getInt("prescribedWeightCentiKg"))
        assertTrue(sets.getJSONObject(3).isNull("prescribedReps"))
        assertEquals(12, sets.getJSONObject(3).getInt("actualReps"))
        assertEquals("SKIPPED", sets.getJSONObject(4).getString("status"))
        assertTrue(sets.getJSONObject(4).isNull("actualWeightCentiKg"))
    }

    private fun workout(
        status: WorkoutSessionStatus,
        exercises: List<SessionExerciseWithSets> = emptyList(),
    ) = WorkoutSessionWithDetails(
        session = WorkoutSessionEntity(
            id = 11,
            sourceWorkoutTemplateId = 1,
            sourceProgramId = 1,
            programNameSnapshot = "Program snapshot",
            workoutNameSnapshot = "Day snapshot",
            startedAt = 1_000,
            completedAt = if (status == WorkoutSessionStatus.ACTIVE) null else 2_000,
            status = status,
            progressionApplied = status != WorkoutSessionStatus.ACTIVE,
            syncId = "00000000-0000-4000-8000-000000000011",
        ),
        exercises = exercises,
    )

    private fun exercise(
        id: Long,
        mode: TrackingMode,
        weight: Int,
        reps: Int,
        duration: Int?,
    ): SessionExerciseWithSets {
        val exercise = SessionExerciseEntity(
            id = id,
            sessionId = 11,
            sourceWorkoutTemplateExerciseId = id,
            exerciseNameSnapshot = "Exercise $id",
            sortOrderSnapshot = id.toInt() - 1,
            plannedSetCountSnapshot = 1,
            repMinSnapshot = reps,
            repMaxSnapshot = reps,
            targetRepsSnapshot = reps,
            prescribedWeightCentiKgSnapshot = weight,
            incrementCentiKgSnapshot = 250,
            restSecondsSnapshot = 60,
            setupNoteSnapshot = "snapshot",
            supersetGroupSnapshot = null,
            supersetRestSecondsSnapshot = null,
            trackingModeSnapshot = mode,
            targetDurationSecondsSnapshot = duration,
            syncId = "00000000-0000-4000-8000-${id.toString().padStart(12, '0')}",
            sourceProgressionTrackSyncId = "10000000-0000-4000-8000-${id.toString().padStart(12, '0')}",
            resultingProgressionWeightCentiKg = if (mode == TrackingMode.WEIGHT_REPS) weight + 250 else null,
            resultingProgressionTargetReps = if (mode == TrackingMode.DURATION) null else reps,
            resultingProgressionDurationSeconds = if (mode == TrackingMode.DURATION) duration!! + 5 else null,
        )
        return SessionExerciseWithSets(
            exercise,
            listOf(
                SessionSetEntity(
                    id = id,
                    sessionExerciseId = id,
                    setOrder = 0,
                    setType = SetType.WORKING,
                    isPlanned = true,
                    countsForProgression = mode != TrackingMode.DURATION,
                    prescribedWeightCentiKg = if (mode == TrackingMode.WEIGHT_REPS) weight else null,
                    prescribedReps = if (mode == TrackingMode.DURATION) null else reps,
                    actualWeightCentiKg = if (mode == TrackingMode.WEIGHT_REPS) weight else null,
                    actualReps = if (mode == TrackingMode.DURATION) null else reps,
                    status = SessionSetStatus.COMPLETED,
                    completedAt = 1_900,
                    prescribedDurationSeconds = duration,
                    actualDurationSeconds = duration,
                    syncId = "20000000-0000-4000-8000-${id.toString().padStart(12, '0')}",
                ),
            ),
        )
    }
}
