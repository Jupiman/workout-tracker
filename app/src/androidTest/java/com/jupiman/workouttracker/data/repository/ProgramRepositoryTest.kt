package com.jupiman.workouttracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import com.jupiman.workouttracker.data.local.entity.WarmupLoadType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.json.JSONObject
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
    fun firstCreatedProgramIsActiveForTheFirstRunSetupFlow() = runTest {
        val programId = repository.createProgram("First Program")

        val activeProgram = database.programDao().observeActive().first()

        assertEquals(programId, activeProgram?.id)
        assertEquals("First Program", activeProgram?.name)
    }

    @Test
    fun creatingExerciseAcceptsFixedRepRange() = runTest {
        val programId = repository.createProgram("Fixed reps")
        val templateId = repository.createWorkoutTemplate(programId, "Day A")

        val templateExerciseId = repository.createExerciseAndAddToWorkout(
            workoutTemplateId = templateId,
            exerciseName = "Bench Press",
            config = config(restSeconds = 180).copy(
                repMin = 8,
                repMax = 8,
                currentTargetReps = 8,
            ),
        )

        val item = database.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(templateId)
            .single { it.id == templateExerciseId }
        assertEquals(8, item.repMin)
        assertEquals(8, item.repMax)
        assertEquals(8, item.currentTargetReps)
    }

    @Test
    fun editingExerciseAcceptsFixedRepRange() = runTest {
        val seed = seedTwoExerciseTemplate()
        val item = database.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(seed.templateId)
            .first { it.id == seed.firstTemplateExerciseId }

        repository.updateTemplateExercise(
            item = item,
            config = config(restSeconds = 180).copy(
                repMin = 8,
                repMax = 8,
                currentTargetReps = 8,
            ),
            setupNote = item.setupNote,
        )

        val updated = database.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(seed.templateId)
            .first { it.id == seed.firstTemplateExerciseId }
        assertEquals(8, updated.repMin)
        assertEquals(8, updated.repMax)
        assertEquals(8, updated.currentTargetReps)
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
                setupNote = secondEditorItem.setupNote,
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
    fun moveWorkoutTemplateReordersTrainingDaysAndCompactsSortOrders() = runTest {
        val programId = repository.createProgram("Current Program")
        val firstDayId = repository.createWorkoutTemplate(programId, "Day A")
        repository.createWorkoutTemplate(programId, "Day B")
        val thirdDayId = repository.createWorkoutTemplate(programId, "Day C")

        repository.moveWorkoutTemplate(
            programId = programId,
            workoutTemplateId = firstDayId,
            offset = 1,
        )

        assertEquals(
            listOf("Day B", "Day A", "Day C"),
            trainingDayNames(programId),
        )

        repository.moveWorkoutTemplate(
            programId = programId,
            workoutTemplateId = thirdDayId,
            offset = -1,
        )

        assertEquals(
            listOf("Day B", "Day C", "Day A"),
            trainingDayNames(programId),
        )

        repository.moveWorkoutTemplate(
            programId = programId,
            workoutTemplateId = firstDayId,
            offset = -2,
        )

        assertEquals(
            listOf("Day A", "Day B", "Day C"),
            trainingDayNames(programId),
        )
        assertEquals(
            listOf(0, 1, 2),
            database.workoutTemplateDao()
                .getForProgram(programId)
                .map { it.sortOrder },
        )
    }

    @Test
    fun moveTemplateExerciseReordersExercisesAndCompactsSortOrders() = runTest {
        val seed = seedTwoExerciseTemplate()
        val thirdTemplateExerciseId = repository.createExerciseAndAddToWorkout(
            workoutTemplateId = seed.templateId,
            exerciseName = "Overhead Press",
            config = config(restSeconds = 150),
        )

        repository.moveTemplateExercise(
            workoutTemplateId = seed.templateId,
            workoutTemplateExerciseId = seed.firstTemplateExerciseId,
            offset = 1,
        )

        assertEquals(
            listOf("Machine Row", "Bench Press", "Overhead Press"),
            editorExerciseNames(seed.templateId),
        )

        repository.moveTemplateExercise(
            workoutTemplateId = seed.templateId,
            workoutTemplateExerciseId = thirdTemplateExerciseId,
            offset = -1,
        )

        assertEquals(
            listOf("Machine Row", "Overhead Press", "Bench Press"),
            editorExerciseNames(seed.templateId),
        )
        assertEquals(
            listOf(0, 1, 2),
            database.workoutTemplateExerciseDao()
                .getForWorkoutTemplate(seed.templateId)
                .map { it.sortOrder },
        )
    }

    @Test
    fun moveSupersetGroupReordersGroupAndKeepsMembersContiguous() = runTest {
        val seed = seedTwoExerciseTemplate()
        repository.supersetWithPrevious(seed.templateId, seed.secondTemplateExerciseId)
        repository.createExerciseAndAddToWorkout(
            workoutTemplateId = seed.templateId,
            exerciseName = "Overhead Press",
            config = config(restSeconds = 150),
        )
        val groupId = database.workoutTemplateExerciseDao()
            .getById(seed.firstTemplateExerciseId)!!
            .supersetGroupId!!

        repository.moveSupersetGroup(
            workoutTemplateId = seed.templateId,
            supersetGroupId = groupId,
            offset = 1,
        )

        assertEquals(
            listOf("Overhead Press", "Bench Press", "Machine Row"),
            editorExerciseNames(seed.templateId),
        )
        assertEquals(
            listOf(null, groupId, groupId),
            database.workoutTemplateExerciseDao()
                .getForWorkoutTemplate(seed.templateId)
                .map { it.supersetGroupId },
        )

        repository.moveSupersetGroup(
            workoutTemplateId = seed.templateId,
            supersetGroupId = groupId,
            offset = -1,
        )

        assertEquals(
            listOf("Bench Press", "Machine Row", "Overhead Press"),
            editorExerciseNames(seed.templateId),
        )
        assertEquals(
            listOf(0, 1, 2),
            database.workoutTemplateExerciseDao()
                .getForWorkoutTemplate(seed.templateId)
                .map { it.sortOrder },
        )
    }

    @Test
    fun moveTemplateExerciseMovesAroundSupersetBlockWithoutSplittingIt() = runTest {
        val seed = seedTwoExerciseTemplate()
        repository.supersetWithPrevious(seed.templateId, seed.secondTemplateExerciseId)
        val thirdTemplateExerciseId = repository.createExerciseAndAddToWorkout(
            workoutTemplateId = seed.templateId,
            exerciseName = "Overhead Press",
            config = config(restSeconds = 150),
        )
        val groupId = database.workoutTemplateExerciseDao()
            .getById(seed.firstTemplateExerciseId)!!
            .supersetGroupId!!

        repository.moveTemplateExercise(
            workoutTemplateId = seed.templateId,
            workoutTemplateExerciseId = thirdTemplateExerciseId,
            offset = -1,
        )

        assertEquals(
            listOf("Overhead Press", "Bench Press", "Machine Row"),
            editorExerciseNames(seed.templateId),
        )
        assertEquals(
            listOf(null, groupId, groupId),
            database.workoutTemplateExerciseDao()
                .getForWorkoutTemplate(seed.templateId)
                .map { it.supersetGroupId },
        )
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
            setupNote = editorItem.setupNote,
        )

        assertEquals(
            emptyList<Long>(),
            database.workoutTemplateSetTargetDao()
                .getForTemplateExercise(seed.firstTemplateExerciseId)
                .map { it.id },
        )
    }

    @Test
    fun presetAndCustomWarmupSchemesCanBeSavedReorderedAndCleared() = runTest {
        val seed = seedTwoExerciseTemplate()

        repository.saveWarmupScheme(
            seed.firstTemplateExerciseId,
            roundingCentiKg = 125,
            sets = WarmupPreset.MINIMAL.sets,
        )

        var warmupSets = database.workoutTemplateWarmupSetDao()
            .getForTemplateExercise(seed.firstTemplateExerciseId)
        assertEquals(listOf(4, 2), warmupSets.map { it.reps })
        assertEquals(listOf(60, 80), warmupSets.map { it.percentOfWorkingWeight })
        assertEquals(
            125,
            database.workoutTemplateExerciseDao().getById(seed.firstTemplateExerciseId)?.warmupRoundingCentiKg,
        )

        repository.saveWarmupScheme(
            seed.firstTemplateExerciseId,
            roundingCentiKg = 250,
            sets = listOf(
                WarmupSetConfiguration(8, WarmupLoadType.FIXED, fixedWeightCentiKg = 2_000),
                WarmupSetConfiguration(5, WarmupLoadType.PERCENTAGE, percentOfWorkingWeight = 50),
                WarmupSetConfiguration(3, WarmupLoadType.PERCENTAGE, percentOfWorkingWeight = 70),
            ),
        )
        warmupSets = database.workoutTemplateWarmupSetDao()
            .getForTemplateExercise(seed.firstTemplateExerciseId)
        assertEquals(listOf(WarmupLoadType.FIXED, WarmupLoadType.PERCENTAGE, WarmupLoadType.PERCENTAGE), warmupSets.map { it.loadType })
        assertEquals(listOf(2_000, null, null), warmupSets.map { it.fixedWeightCentiKg })
        assertEquals(listOf(null, 50, 70), warmupSets.map { it.percentOfWorkingWeight })

        repository.saveWarmupScheme(
            seed.firstTemplateExerciseId,
            roundingCentiKg = 250,
            sets = listOf(
                WarmupSetConfiguration(3, WarmupLoadType.PERCENTAGE, percentOfWorkingWeight = 70),
                WarmupSetConfiguration(8, WarmupLoadType.FIXED, fixedWeightCentiKg = 2_000),
                WarmupSetConfiguration(5, WarmupLoadType.PERCENTAGE, percentOfWorkingWeight = 50),
            ),
        )
        warmupSets = database.workoutTemplateWarmupSetDao()
            .getForTemplateExercise(seed.firstTemplateExerciseId)
        assertEquals(listOf(0, 1, 2), warmupSets.map { it.sortOrder })
        assertEquals(listOf(3, 8, 5), warmupSets.map { it.reps })
        assertEquals(listOf(70, null, 50), warmupSets.map { it.percentOfWorkingWeight })
        assertEquals(listOf(null, 2_000, null), warmupSets.map { it.fixedWeightCentiKg })

        repository.clearWarmupScheme(seed.firstTemplateExerciseId)

        assertEquals(
            emptyList<Long>(),
            database.workoutTemplateWarmupSetDao()
                .getForTemplateExercise(seed.firstTemplateExerciseId)
                .map { it.id },
        )
    }

    @Test
    fun changingAwayFromWeightRepsRemovesWarmups() = runTest {
        val seed = seedTwoExerciseTemplate()
        repository.saveWarmupScheme(seed.firstTemplateExerciseId, 500, WarmupPreset.STANDARD.sets)
        val item = database.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(seed.templateId)
            .first { it.id == seed.firstTemplateExerciseId }

        repository.updateTemplateExercise(
            item = item,
            config = config(restSeconds = 180).copy(
                trackingMode = com.jupiman.workouttracker.data.local.entity.TrackingMode.REPS,
                currentWeightCentiKg = 0,
            ),
            setupNote = item.setupNote,
        )

        assertEquals(
            emptyList<Long>(),
            database.workoutTemplateWarmupSetDao()
                .getForTemplateExercise(seed.firstTemplateExerciseId).map { it.id },
        )
    }

    @Test
    fun fullBackupRoundTripPreservesAdvancedWarmups() = runTest {
        val seed = seedTwoExerciseTemplate()
        val item = database.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(seed.templateId)
            .first { it.id == seed.firstTemplateExerciseId }
        repository.updateTemplateExercise(
            item = item,
            config = config(restSeconds = 180).copy(
                repMin = 8,
                repMax = 8,
                currentTargetReps = 8,
            ),
            setupNote = item.setupNote,
        )
        repository.saveWarmupScheme(
            seed.firstTemplateExerciseId,
            125,
            listOf(
                WarmupSetConfiguration(8, WarmupLoadType.FIXED, fixedWeightCentiKg = 2_000),
                WarmupSetConfiguration(3, WarmupLoadType.PERCENTAGE, percentOfWorkingWeight = 70),
            ),
        )
        val output = ByteArrayOutputStream()
        DataBackupRepository(database).exportBackup(output)

        DataBackupRepository(database).restoreBackup(ByteArrayInputStream(output.toByteArray()))

        val restoredExercise = database.workoutTemplateExerciseDao().getById(seed.firstTemplateExerciseId)!!
        val restoredWarmups = database.workoutTemplateWarmupSetDao()
            .getForTemplateExercise(seed.firstTemplateExerciseId)
        val restoredProgression = database.progressionStateDao()
            .getForTemplateExercise(seed.firstTemplateExerciseId)!!
        assertEquals(8, restoredExercise.repMin)
        assertEquals(8, restoredExercise.repMax)
        assertEquals(8, restoredProgression.currentTargetReps)
        assertEquals(125, restoredExercise.warmupRoundingCentiKg)
        assertEquals(listOf(WarmupLoadType.FIXED, WarmupLoadType.PERCENTAGE), restoredWarmups.map { it.loadType })
        assertEquals(listOf(2_000, null), restoredWarmups.map { it.fixedWeightCentiKg })
        assertEquals(listOf(null, 70), restoredWarmups.map { it.percentOfWorkingWeight })
    }

    @Test
    fun versionFourBackupRestoresLegacyPercentageWarmups() = runTest {
        val seed = seedTwoExerciseTemplate()
        repository.saveWarmupScheme(seed.firstTemplateExerciseId, 500, WarmupPreset.STANDARD.sets)
        val output = ByteArrayOutputStream()
        DataBackupRepository(database).exportBackup(output)
        val backup = JSONObject(output.toString(Charsets.UTF_8.name()))
            .put("formatVersion", 4)
            .put("schemaVersion", 8)
        val tables = backup.getJSONObject("tables")
        val exercises = tables.getJSONArray("workout_template_exercises")
        for (index in 0 until exercises.length()) {
            exercises.getJSONObject(index).remove("warmupRoundingCentiKg")
        }
        val warmups = tables.getJSONArray("workout_template_warmup_sets")
        for (index in 0 until warmups.length()) {
            warmups.getJSONObject(index).apply {
                remove("loadType")
                remove("fixedWeightCentiKg")
            }
        }

        DataBackupRepository(database).restoreBackup(ByteArrayInputStream(backup.toString().toByteArray()))

        val restoredExercise = database.workoutTemplateExerciseDao().getById(seed.firstTemplateExerciseId)!!
        val restoredWarmups = database.workoutTemplateWarmupSetDao()
            .getForTemplateExercise(seed.firstTemplateExerciseId)
        assertEquals(500, restoredExercise.warmupRoundingCentiKg)
        assertEquals(listOf(50, 70, 85), restoredWarmups.map { it.percentOfWorkingWeight })
        assertEquals(listOf(WarmupLoadType.PERCENTAGE, WarmupLoadType.PERCENTAGE, WarmupLoadType.PERCENTAGE), restoredWarmups.map { it.loadType })
    }

    @Test
    fun duplicateWorkoutTemplateCopiesExercisesSupersetsTargetsWarmupsAndSetupNotesIndependently() = runTest {
        val seed = seedTwoExerciseTemplate()
        repository.supersetWithPrevious(seed.templateId, seed.secondTemplateExerciseId)
        val firstEditorItem = database.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(seed.templateId)
            .first { it.id == seed.firstTemplateExerciseId }
        repository.updateTemplateExercise(
            item = firstEditorItem,
            config = config(restSeconds = 180),
            setupNote = "Seat 4",
        )
        repository.updateTemplateSetTarget(
            workoutTemplateExerciseId = seed.firstTemplateExerciseId,
            setOrder = 1,
            prescribedWeightCentiKg = 7250,
            prescribedReps = 9,
            countsForProgression = false,
        )
        repository.saveWarmupScheme(seed.firstTemplateExerciseId, 500, WarmupPreset.STANDARD.sets)

        val copyId = repository.duplicateWorkoutTemplate(seed.templateId)

        val templates = database.workoutTemplateDao().getForProgram(
            database.workoutTemplateDao().getById(seed.templateId)!!.programId,
        )
        val copiedExercises = database.workoutTemplateExerciseDao().getForWorkoutTemplate(copyId)
        val copiedFirst = copiedExercises[0]
        val copiedSecond = copiedExercises[1]
        val copiedProgression = database.progressionStateDao().getForTemplateExercise(copiedFirst.id)
        val copiedTargets = database.workoutTemplateSetTargetDao().getForTemplateExercise(copiedFirst.id)
        val copiedWarmups = database.workoutTemplateWarmupSetDao().getForTemplateExercise(copiedFirst.id)
        val sourceFirst = database.workoutTemplateExerciseDao().getById(seed.firstTemplateExerciseId)!!

        assertEquals(listOf("Day A", "Day A copy"), templates.map { it.name })
        assertEquals(listOf(0, 1), templates.map { it.sortOrder })
        assertEquals(2, copiedExercises.size)
        assertEquals(listOf("Bench Press", "Machine Row"), editorExerciseNames(copyId))
        assertEquals("Seat 4", copiedFirst.setupNote)
        assertEquals(false, seed.firstTemplateExerciseId == copiedFirst.id)
        assertEquals(false, sourceFirst.syncId == copiedFirst.syncId)
        assertEquals(sourceFirst.exerciseId, copiedFirst.exerciseId)
        assertNotNull(copiedFirst.supersetGroupId)
        assertEquals(copiedFirst.supersetGroupId, copiedSecond.supersetGroupId)
        assertEquals(false, copiedFirst.supersetGroupId == sourceFirst.supersetGroupId)
        assertEquals(7000, copiedProgression?.currentWeightCentiKg)
        assertEquals(10, copiedProgression?.currentTargetReps)
        assertEquals(listOf(1), copiedTargets.map { it.setOrder })
        assertEquals(7250, copiedTargets.single().prescribedWeightCentiKg)
        assertEquals(9, copiedTargets.single().prescribedReps)
        assertEquals(false, copiedTargets.single().countsForProgression)
        assertEquals(listOf(5, 3, 1), copiedWarmups.map { it.reps })
        assertEquals(listOf(50, 70, 85), copiedWarmups.map { it.percentOfWorkingWeight })
        assertEquals(500, copiedFirst.warmupRoundingCentiKg)

        repository.updateTemplateExercise(
            item = database.workoutTemplateExerciseDao()
                .getEditorItemsForWorkoutTemplate(copyId)
                .first { it.id == copiedFirst.id },
            config = config(restSeconds = 180).copy(currentTargetReps = 11),
            setupNote = copiedFirst.setupNote,
        )

        val sourceProgression = database.progressionStateDao().getForTemplateExercise(seed.firstTemplateExerciseId)
        val updatedCopiedProgression = database.progressionStateDao().getForTemplateExercise(copiedFirst.id)
        assertEquals(10, sourceProgression?.currentTargetReps)
        assertEquals(11, updatedCopiedProgression?.currentTargetReps)
    }

    @Test
    fun duplicateTemplateExerciseCopiesConfigurationAsStandaloneIndependentExercise() = runTest {
        val seed = seedTwoExerciseTemplate()
        repository.supersetWithPrevious(seed.templateId, seed.secondTemplateExerciseId)
        val firstEditorItem = database.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(seed.templateId)
            .first { it.id == seed.firstTemplateExerciseId }
        repository.updateTemplateExercise(
            item = firstEditorItem,
            config = config(restSeconds = 180).copy(currentWeightCentiKg = 7500),
            setupNote = "Bench 3",
        )
        repository.updateTemplateSetTarget(
            workoutTemplateExerciseId = seed.firstTemplateExerciseId,
            setOrder = 0,
            prescribedWeightCentiKg = 7500,
            prescribedReps = 8,
        )
        repository.saveWarmupScheme(seed.firstTemplateExerciseId, 125, WarmupPreset.HEAVY.sets)

        val copyExerciseId = repository.duplicateTemplateExercise(seed.firstTemplateExerciseId)

        val exercises = database.workoutTemplateExerciseDao().getForWorkoutTemplate(seed.templateId)
        val source = database.workoutTemplateExerciseDao().getById(seed.firstTemplateExerciseId)!!
        val copy = database.workoutTemplateExerciseDao().getById(copyExerciseId)!!
        val copiedProgression = database.progressionStateDao().getForTemplateExercise(copyExerciseId)
        val copiedTargets = database.workoutTemplateSetTargetDao().getForTemplateExercise(copyExerciseId)
        val copiedWarmups = database.workoutTemplateWarmupSetDao().getForTemplateExercise(copyExerciseId)

        assertEquals(3, exercises.size)
        assertEquals(2, copy.sortOrder)
        assertEquals(source.exerciseId, copy.exerciseId)
        assertEquals(source.plannedWorkingSets, copy.plannedWorkingSets)
        assertEquals(source.repMin, copy.repMin)
        assertEquals(source.repMax, copy.repMax)
        assertEquals(source.incrementCentiKg, copy.incrementCentiKg)
        assertEquals(source.restSeconds, copy.restSeconds)
        assertEquals("Bench 3", copy.setupNote)
        assertNull(copy.supersetGroupId)
        assertEquals(7500, copiedProgression?.currentWeightCentiKg)
        assertEquals(10, copiedProgression?.currentTargetReps)
        assertEquals(listOf(0), copiedTargets.map { it.setOrder })
        assertEquals(7500, copiedTargets.single().prescribedWeightCentiKg)
        assertEquals(8, copiedTargets.single().prescribedReps)
        assertEquals(listOf(5, 3, 2, 1), copiedWarmups.map { it.reps })
        assertEquals(125, copy.warmupRoundingCentiKg)

        repository.updateTemplateExercise(
            item = database.workoutTemplateExerciseDao()
                .getEditorItemsForWorkoutTemplate(seed.templateId)
                .first { it.id == copyExerciseId },
            config = config(restSeconds = 180).copy(currentWeightCentiKg = 8000),
            setupNote = copy.setupNote,
        )

        val sourceProgression = database.progressionStateDao().getForTemplateExercise(seed.firstTemplateExerciseId)
        val updatedCopiedProgression = database.progressionStateDao().getForTemplateExercise(copyExerciseId)
        assertEquals(7500, sourceProgression?.currentWeightCentiKg)
        assertEquals(8000, updatedCopiedProgression?.currentWeightCentiKg)
    }

    @Test
    fun changingDuplicatedTemplateExerciseNameDoesNotRenameSourceExercise() = runTest {
        val seed = seedTwoExerciseTemplate()

        val copyExerciseId = repository.duplicateTemplateExercise(seed.firstTemplateExerciseId)
        val copyEditorItem = database.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(seed.templateId)
            .first { it.id == copyExerciseId }

        repository.updateTemplateExercise(
            item = copyEditorItem,
            exerciseName = "Incline Bench Press",
            config = config(restSeconds = 180),
            setupNote = copyEditorItem.setupNote,
        )

        val source = database.workoutTemplateExerciseDao().getById(seed.firstTemplateExerciseId)!!
        val copy = database.workoutTemplateExerciseDao().getById(copyExerciseId)!!
        val editorItems = database.workoutTemplateExerciseDao().getEditorItemsForWorkoutTemplate(seed.templateId)

        assertEquals("Bench Press", editorItems.first { it.id == source.id }.exerciseName)
        assertEquals("Incline Bench Press", editorItems.first { it.id == copy.id }.exerciseName)
        assertEquals(false, source.exerciseId == copy.exerciseId)
    }

    @Test
    fun progressTracksKeepSameLibraryExerciseSeparateByTrainingDay() = runTest {
        val firstProgramId = repository.createProgram("Push")
        val firstDayId = repository.createWorkoutTemplate(firstProgramId, "Day A")
        val firstTrackId = repository.createExerciseAndAddToWorkout(
            workoutTemplateId = firstDayId,
            exerciseName = "Bench Press",
            config = config(restSeconds = 180),
        )
        val exerciseId = database.workoutTemplateExerciseDao().getById(firstTrackId)!!.exerciseId
        val secondProgramId = repository.createProgram("Strength")
        val secondDayId = repository.createWorkoutTemplate(secondProgramId, "Day C")
        val secondTrackId = repository.addExistingExerciseToWorkout(
            workoutTemplateId = secondDayId,
            exerciseId = exerciseId,
            config = config(restSeconds = 120).copy(currentWeightCentiKg = 9_000),
        )

        val tracks = database.workoutTemplateExerciseDao().observeProgressTracksForExercise(exerciseId).first()

        assertEquals(setOf(firstTrackId, secondTrackId), tracks.map { it.workoutTemplateExerciseId }.toSet())
        assertEquals(setOf("Push · Day A", "Strength · Day C"), tracks.map { "${it.programName} · ${it.workoutName}" }.toSet())
        assertEquals(9_000, tracks.first { it.workoutTemplateExerciseId == secondTrackId }.currentWeightCentiKg)
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

    private suspend fun editorExerciseNames(templateId: Long): List<String> =
        database.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(templateId)
            .map { it.exerciseName }

    private suspend fun trainingDayNames(programId: Long): List<String> =
        database.workoutTemplateDao()
            .getForProgram(programId)
            .map { it.name }

    private data class TwoExerciseTemplateSeed(
        val templateId: Long,
        val firstTemplateExerciseId: Long,
        val secondTemplateExerciseId: Long,
    )
}
