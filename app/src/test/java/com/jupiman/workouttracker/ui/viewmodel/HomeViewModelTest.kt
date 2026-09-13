package com.jupiman.workouttracker.ui.viewmodel

import com.jupiman.workouttracker.data.local.entity.SessionExerciseEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.model.SessionExerciseWithSets
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun recommendsFirstTemplateWhenThereIsNoFinishedSession() {
        val templates = templates()

        val result = recommendNextWorkoutTemplate(templates, latestFinishedSession = null)

        assertEquals("Day A", result?.name)
    }

    @Test
    fun recommendsTemplateAfterLatestFinishedSession() {
        val templates = templates()

        val result = recommendNextWorkoutTemplate(
            templates = templates,
            latestFinishedSession = finishedSession(sourceWorkoutTemplateId = 1),
        )

        assertEquals("Day B", result?.name)
    }

    @Test
    fun wrapsToFirstTemplateAfterLastTemplate() {
        val templates = templates()

        val result = recommendNextWorkoutTemplate(
            templates = templates,
            latestFinishedSession = finishedSession(sourceWorkoutTemplateId = 2),
        )

        assertEquals("Day A", result?.name)
    }

    @Test
    fun fallsBackToFirstTemplateWhenPreviousTemplateNoLongerExists() {
        val templates = templates()

        val result = recommendNextWorkoutTemplate(
            templates = templates,
            latestFinishedSession = finishedSession(sourceWorkoutTemplateId = 99),
        )

        assertEquals("Day A", result?.name)
    }

    @Test
    fun returnsNullWhenThereAreNoTemplates() {
        val result = recommendNextWorkoutTemplate(
            templates = emptyList(),
            latestFinishedSession = finishedSession(sourceWorkoutTemplateId = 1),
        )

        assertNull(result)
    }

    @Test
    fun lastTimeContextMatchesSameTemplateExerciseInstance() {
        val activeWorkout = workoutWithDetails(
            sessionId = 10,
            status = WorkoutSessionStatus.ACTIVE,
            startedAt = 1_000,
            completedAt = null,
            sourceTemplateExerciseId = 100,
            actualWeightCentiKg = null,
            actualReps = null,
        )
        val previous = workoutWithDetails(
            sessionId = 9,
            status = WorkoutSessionStatus.COMPLETED,
            startedAt = 100,
            completedAt = 200,
            sourceTemplateExerciseId = 100,
            actualWeightCentiKg = 7_250,
            actualReps = 11,
        )

        val result = lastTimeContextsForActiveWorkout(activeWorkout, listOf(previous))

        val context = result.getValue(100)
        assertEquals(200L, context.completedAt)
        assertEquals("Workout", context.workoutName)
        assertEquals(1, context.sets.size)
        assertEquals(7_250, context.sets.single().weightCentiKg)
        assertEquals(11, context.sets.single().reps)
    }

    @Test
    fun lastTimeContextDoesNotMixDifferentTemplateExerciseInstances() {
        val activeWorkout = workoutWithDetails(
            sessionId = 10,
            status = WorkoutSessionStatus.ACTIVE,
            startedAt = 1_000,
            completedAt = null,
            sourceTemplateExerciseId = 100,
            actualWeightCentiKg = null,
            actualReps = null,
        )
        val differentInstance = workoutWithDetails(
            sessionId = 9,
            status = WorkoutSessionStatus.COMPLETED,
            startedAt = 100,
            completedAt = 200,
            sourceTemplateExerciseId = 200,
            actualWeightCentiKg = 12_000,
            actualReps = 8,
        )

        val result = lastTimeContextsForActiveWorkout(activeWorkout, listOf(differentInstance))

        assertEquals(emptyMap<Long, LastTimeExerciseContext>(), result)
    }

    @Test
    fun lastTimeContextUsesMostRecentHistorySnapshot() {
        val activeWorkout = workoutWithDetails(
            sessionId = 10,
            status = WorkoutSessionStatus.ACTIVE,
            startedAt = 1_000,
            completedAt = null,
            sourceTemplateExerciseId = 100,
            actualWeightCentiKg = null,
            actualReps = null,
        )
        val older = workoutWithDetails(
            sessionId = 8,
            status = WorkoutSessionStatus.COMPLETED,
            startedAt = 100,
            completedAt = 200,
            sourceTemplateExerciseId = 100,
            actualWeightCentiKg = 7_000,
            actualReps = 9,
        )
        val newer = workoutWithDetails(
            sessionId = 9,
            status = WorkoutSessionStatus.PARTIAL,
            startedAt = 300,
            completedAt = 400,
            sourceTemplateExerciseId = 100,
            actualWeightCentiKg = 7_500,
            actualReps = 10,
        )

        val result = lastTimeContextsForActiveWorkout(activeWorkout, listOf(older, newer))

        val context = result.getValue(100)
        assertEquals(400L, context.completedAt)
        assertEquals(WorkoutSessionStatus.PARTIAL, context.status)
        assertEquals(7_500, context.sets.single().weightCentiKg)
        assertEquals(10, context.sets.single().reps)
    }

    private fun templates() = listOf(
        WorkoutTemplateEntity(id = 1, programId = 1, name = "Day A", sortOrder = 0),
        WorkoutTemplateEntity(id = 2, programId = 1, name = "Day B", sortOrder = 1),
    )

    private fun finishedSession(sourceWorkoutTemplateId: Long) = WorkoutSessionEntity(
        id = 1,
        sourceWorkoutTemplateId = sourceWorkoutTemplateId,
        sourceProgramId = 1,
        programNameSnapshot = "Program",
        workoutNameSnapshot = "Workout",
        startedAt = 1,
        completedAt = 2,
        status = WorkoutSessionStatus.COMPLETED,
        progressionApplied = true,
    )

    private fun workoutWithDetails(
        sessionId: Long,
        status: WorkoutSessionStatus,
        startedAt: Long,
        completedAt: Long?,
        sourceTemplateExerciseId: Long,
        actualWeightCentiKg: Int?,
        actualReps: Int?,
    ) = WorkoutSessionWithDetails(
        session = WorkoutSessionEntity(
            id = sessionId,
            sourceWorkoutTemplateId = 1,
            sourceProgramId = 1,
            programNameSnapshot = "Program",
            workoutNameSnapshot = "Workout",
            startedAt = startedAt,
            completedAt = completedAt,
            status = status,
            progressionApplied = status != WorkoutSessionStatus.ACTIVE,
        ),
        exercises = listOf(
            SessionExerciseWithSets(
                exercise = SessionExerciseEntity(
                    id = sessionId * 10,
                    sessionId = sessionId,
                    sourceWorkoutTemplateExerciseId = sourceTemplateExerciseId,
                    exerciseNameSnapshot = "Bench Press",
                    sortOrderSnapshot = 0,
                    plannedSetCountSnapshot = 1,
                    repMinSnapshot = 8,
                    repMaxSnapshot = 12,
                    targetRepsSnapshot = 10,
                    prescribedWeightCentiKgSnapshot = 7_000,
                    incrementCentiKgSnapshot = 250,
                    restSecondsSnapshot = 120,
                    supersetGroupSnapshot = null,
                    supersetRestSecondsSnapshot = null,
                ),
                sets = listOf(
                    SessionSetEntity(
                        id = sessionId * 100,
                        sessionExerciseId = sessionId * 10,
                        setOrder = 0,
                        setType = SetType.WORKING,
                        isPlanned = true,
                        countsForProgression = true,
                        prescribedWeightCentiKg = 7_000,
                        prescribedReps = 10,
                        actualWeightCentiKg = actualWeightCentiKg,
                        actualReps = actualReps,
                        status = if (actualWeightCentiKg == null || actualReps == null) {
                            SessionSetStatus.PENDING
                        } else {
                            SessionSetStatus.COMPLETED
                        },
                        completedAt = completedAt,
                    ),
                ),
            ),
        ),
    )
}
