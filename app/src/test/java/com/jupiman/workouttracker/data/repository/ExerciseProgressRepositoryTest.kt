package com.jupiman.workouttracker.data.repository

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
import org.junit.Test

class ExerciseProgressRepositoryTest {
    @Test
    fun weightMetricUsesOnlyCompletedProgressionWorkingSets() {
        val history = listOf(
            workout(
                sessionId = 1,
                trackId = 10,
                mode = TrackingMode.WEIGHT_REPS,
                sets = listOf(
                    set(SetType.WARMUP, weight = 9_000),
                    set(SetType.WORKING, weight = 7_000),
                    set(SetType.WORKING, weight = 7_250),
                    set(SetType.WORKING, weight = 12_000, countsForProgression = false),
                    set(SetType.EXTRA, weight = 15_000),
                    set(SetType.AMRAP, weight = 18_000),
                    set(SetType.DROP, weight = 20_000),
                ),
            ),
        )

        val result = buildExerciseProgressSessions(history, 10)

        assertEquals(7_250, result.single().graphValue)
    }

    @Test
    fun templateExerciseIdentityIsolatesSameNamedTracks() {
        val history = listOf(
            workout(1, 10, TrackingMode.WEIGHT_REPS, listOf(set(SetType.WORKING, weight = 7_000))),
            workout(2, 20, TrackingMode.WEIGHT_REPS, listOf(set(SetType.WORKING, weight = 9_000))),
        )

        val result = buildExerciseProgressSessions(history, 10)

        assertEquals(listOf(1L), result.map { it.sessionId })
        assertEquals(7_000, result.single().graphValue)
    }

    @Test
    fun repsAndDurationUseTheirSnapshotMetrics() {
        val reps = workout(
            1,
            10,
            TrackingMode.REPS,
            listOf(set(SetType.WORKING, reps = 8), set(SetType.WORKING, reps = 11)),
        )
        val duration = workout(
            2,
            20,
            TrackingMode.DURATION,
            listOf(
                set(SetType.WORKING, duration = 41, countsForProgression = false),
                set(SetType.WORKING, duration = 60, countsForProgression = false),
            ),
        )

        assertEquals(11, buildExerciseProgressSessions(listOf(reps), 10).single().graphValue)
        assertEquals(60, buildExerciseProgressSessions(listOf(duration), 20).single().graphValue)
    }

    @Test
    fun trackingModeEditsDoNotHideOlderSnapshotHistory() {
        val history = listOf(
            workout(1, 10, TrackingMode.WEIGHT_REPS, listOf(set(SetType.WORKING, weight = 7_000))),
            workout(2, 10, TrackingMode.REPS, listOf(set(SetType.WORKING, reps = 12))),
        )

        val result = buildExerciseProgressSessions(history, 10)

        assertEquals(listOf(2L, 1L), result.map { it.sessionId })
        assertEquals(listOf(TrackingMode.REPS, TrackingMode.WEIGHT_REPS), result.map { it.trackingMode })
    }

    private fun workout(
        sessionId: Long,
        trackId: Long,
        mode: TrackingMode,
        sets: List<SessionSetEntity>,
    ): WorkoutSessionWithDetails = WorkoutSessionWithDetails(
        session = WorkoutSessionEntity(
            id = sessionId,
            sourceWorkoutTemplateId = 1,
            sourceProgramId = 1,
            programNameSnapshot = "Program",
            workoutNameSnapshot = "Day",
            startedAt = sessionId * 1_000,
            completedAt = sessionId * 1_000 + 500,
            status = WorkoutSessionStatus.COMPLETED,
        ),
        exercises = listOf(
            SessionExerciseWithSets(
                exercise = SessionExerciseEntity(
                    id = sessionId,
                    sessionId = sessionId,
                    sourceWorkoutTemplateExerciseId = trackId,
                    exerciseNameSnapshot = "Bench Press",
                    sortOrderSnapshot = 0,
                    plannedSetCountSnapshot = 3,
                    repMinSnapshot = 8,
                    repMaxSnapshot = 12,
                    targetRepsSnapshot = 10,
                    prescribedWeightCentiKgSnapshot = 7_000,
                    incrementCentiKgSnapshot = 250,
                    restSecondsSnapshot = 120,
                    setupNoteSnapshot = "",
                    supersetGroupSnapshot = null,
                    supersetRestSecondsSnapshot = null,
                    trackingModeSnapshot = mode,
                    targetDurationSecondsSnapshot = if (mode == TrackingMode.DURATION) 60 else null,
                ),
                sets = sets.map { it.copy(sessionExerciseId = sessionId) },
            ),
        ),
    )

    private fun set(
        type: SetType,
        weight: Int? = null,
        reps: Int? = null,
        duration: Int? = null,
        countsForProgression: Boolean = type == SetType.WORKING,
    ) = SessionSetEntity(
        sessionExerciseId = 0,
        setOrder = 0,
        setType = type,
        isPlanned = type == SetType.WORKING,
        countsForProgression = countsForProgression,
        prescribedWeightCentiKg = weight,
        prescribedReps = reps,
        actualWeightCentiKg = weight,
        actualReps = reps,
        status = SessionSetStatus.COMPLETED,
        completedAt = 2_000,
        prescribedDurationSeconds = duration,
        actualDurationSeconds = duration,
    )
}
