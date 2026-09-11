package com.jupiman.workouttracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ProgramRepositoryTest {
    private lateinit var database: WorkoutTrackerDatabase
    private lateinit var repository: ProgramRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WorkoutTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ProgramRepository(
            database = database,
            programDao = database.programDao(),
            workoutTemplateDao = database.workoutTemplateDao(),
            workoutTemplateExerciseDao = database.workoutTemplateExerciseDao(),
            workoutTemplateSetTargetDao = database.workoutTemplateSetTargetDao(),
            workoutTemplateWarmupSetDao = database.workoutTemplateWarmupSetDao(),
            progressionStateDao = database.progressionStateDao(),
            supersetGroupDao = database.supersetGroupDao(),
            exerciseDao = database.exerciseDao(),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun supersetWithPreviousGroupsAdjacentMatchingExercises() = runTest {
        val seed = seedTwoExerciseTemplate()

        repository.supersetWithPrevious(seed.templateId, seed.secondTemplateExerciseId)

        val first = database.workoutTemplateExerciseDao().getById(seed.firstTemplateExerciseId)!!
        val second = database.workoutTemplateExerciseDao().getById(seed.secondTemplateExerciseId)!!
        val groupId = first.supersetGroupId

        assertNotNull(groupId)
        assertEquals(groupId, second.supersetGroupId)
        assertEquals(180, database.supersetGroupDao().getById(groupId!!)?.restSeconds)
    }

    @Test
    fun removeFromSupersetDeletesTwoExerciseGroup() = runTest {
        val seed = seedTwoExerciseTemplate()
        repository.supersetWithPrevious(seed.templateId, seed.secondTemplateExerciseId)

        repository.removeFromSuperset(seed.secondTemplateExerciseId)

        val first = database.workoutTemplateExerciseDao().getById(seed.firstTemplateExerciseId)!!
        val second = database.workoutTemplateExerciseDao().getById(seed.secondTemplateExerciseId)!!
        assertNull(first.supersetGroupId)
        assertNull(second.supersetGroupId)
        assertEquals(emptyList<Long>(), database.supersetGroupDao().getForWorkoutTemplate(seed.templateId).map { it.id })
    }

    @Test
    fun supersetWithPreviousRejectsMismatchedWorkingSetCounts() = runTest {
        val seed = seedTwoExerciseTemplate(secondPlannedWorkingSets = 2)

        try {
            repository.supersetWithPrevious(seed.templateId, seed.secondTemplateExerciseId)
            fail("Expected mismatched set counts to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Superset exercises must have the same number of working sets.", expected.message)
        }

        assertEquals(emptyList<Long>(), database.supersetGroupDao().getForWorkoutTemplate(seed.templateId).map { it.id })
    }

    @Test
    fun updatingSupersetMemberToMismatchedWorkingSetCountFails() = runTest {
        val seed = seedTwoExerciseTemplate()
        repository.supersetWithPrevious(seed.templateId, seed.secondTemplateExerciseId)
        val secondEditorItem = database.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(seed.templateId)
            .first { it.id == seed.secondTemplateExerciseId }

        try {
            repository.updateTemplateExercise(
                item = secondEditorItem,
                config = config(plannedWorkingSets = 2, restSeconds = 120),
            )
            fail("Expected mismatched set count update to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Superset exercises must have the same number of working sets.", expected.message)
        }

        val second = database.workoutTemplateExerciseDao().getById(seed.secondTemplateExerciseId)!!
        assertEquals(3, second.plannedWorkingSets)
    }

    @Test
    fun removingTemplateExercisePrunesOrphanedSupersetGroup() = runTest {
        val seed = seedTwoExerciseTemplate()
        repository.supersetWithPrevious(seed.templateId, seed.secondTemplateExerciseId)

        repository.removeTemplateExercise(seed.secondTemplateExerciseId)

        val first = database.workoutTemplateExerciseDao().getById(seed.firstTemplateExerciseId)!!
        assertNull(first.supersetGroupId)
        assertEquals(emptyList<Long>(), database.supersetGroupDao().getForWorkoutTemplate(seed.templateId).map { it.id })
    }

    @Test
    fun setTargetsCanBeSavedAndReset() = runTest {
        val seed = seedTwoExerciseTemplate()

        repository.updateTemplateSetTarget(
            workoutTemplateExerciseId = seed.firstTemplateExerciseId,
            setOrder = 1,
            prescribedWeightCentiKg = 7250,
            prescribedReps = 9,
        )

        val savedTargets = database.workoutTemplateSetTargetDao()
            .getForTemplateExercise(seed.firstTemplateExerciseId)
        assertEquals(1, savedTargets.size)
        assertEquals(1, savedTargets.single().setOrder)
        assertEquals(7250, savedTargets.single().prescribedWeightCentiKg)
        assertEquals(9, savedTargets.single().prescribedReps)

        repository.resetTemplateSetTarget(seed.firstTemplateExerciseId, setOrder = 1)

        assertEquals(
            emptyList<Long>(),
            database.workoutTemplateSetTargetDao()
                .getForTemplateExercise(seed.firstTemplateExerciseId)
                .map { it.id },
        )
    }

    @Test
    fun reducingWorkingSetCountPrunesOutOfRangeSetTargets() = runTest {
        val seed = seedTwoExerciseTemplate()
        val editorItem = database.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(seed.templateId)
            .first { it.id == seed.firstTemplateExerciseId }
        repository.updateTemplateSetTarget(
            workoutTemplateExerciseId = seed.firstTemplateExerciseId,
            setOrder = 2,
            prescribedWeightCentiKg = 7250,
            prescribedReps = 9,
        )

        repository.updateTemplateExercise(
            item = editorItem,
            config = config(plannedWorkingSets = 2, restSeconds = 180),
        )

        assertEquals(
            emptyList<Long>(),
            database.workoutTemplateSetTargetDao()
                .getForTemplateExercise(seed.firstTemplateExerciseId)
                .map { it.id },
        )
    }

    @Test
    fun defaultWarmupSchemeCanBeEnabledAndCleared() = runTest {
        val seed = seedTwoExerciseTemplate()

        repository.enableDefaultWarmupScheme(seed.firstTemplateExerciseId)

        val warmupSets = database.workoutTemplateWarmupSetDao()
            .getForTemplateExercise(seed.firstTemplateExerciseId)
        assertEquals(listOf(10, 3, 3), warmupSets.map { it.reps })
        assertEquals(listOf(30, 70, 75), warmupSets.map { it.percentOfWorkingWeight })

        repository.clearWarmupScheme(seed.firstTemplateExerciseId)

        assertEquals(
            emptyList<Long>(),
            database.workoutTemplateWarmupSetDao()
                .getForTemplateExercise(seed.firstTemplateExerciseId)
                .map { it.id },
        )
    }

    private suspend fun seedTwoExerciseTemplate(secondPlannedWorkingSets: Int = 3): TwoExerciseTemplateSeed {
        val programId = repository.createProgram("Current Program")
        val templateId = repository.createWorkoutTemplate(programId, "Day A")
        val firstTemplateExerciseId = repository.createExerciseAndAddToWorkout(
            workoutTemplateId = templateId,
            exerciseName = "Bench Press",
            config = config(restSeconds = 180),
        )
        val secondTemplateExerciseId = repository.createExerciseAndAddToWorkout(
            workoutTemplateId = templateId,
            exerciseName = "Machine Row",
            config = config(plannedWorkingSets = secondPlannedWorkingSets, restSeconds = 120),
        )

        return TwoExerciseTemplateSeed(
            templateId = templateId,
            firstTemplateExerciseId = firstTemplateExerciseId,
            secondTemplateExerciseId = secondTemplateExerciseId,
        )
    }

    private fun config(
        plannedWorkingSets: Int = 3,
        restSeconds: Int,
    ) = TemplateExerciseConfig(
        plannedWorkingSets = plannedWorkingSets,
        repMin = 8,
        repMax = 12,
        incrementCentiKg = 250,
        restSeconds = restSeconds,
        currentWeightCentiKg = 7000,
        currentTargetReps = 10,
    )

    private data class TwoExerciseTemplateSeed(
        val templateId: Long,
        val firstTemplateExerciseId: Long,
        val secondTemplateExerciseId: Long,
    )
}
