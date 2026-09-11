package com.jupiman.workouttracker.ui.viewmodel

import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
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
}
