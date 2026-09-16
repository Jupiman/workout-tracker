package com.jupiman.workouttracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.TrackingMode
import com.jupiman.workouttracker.data.local.entity.WarmupLoadType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ProgramTransferRepositoryTest {
    private lateinit var sourceDatabase: WorkoutTrackerDatabase
    private lateinit var targetDatabase: WorkoutTrackerDatabase
    private lateinit var sourcePrograms: ProgramRepository
    private lateinit var targetPrograms: ProgramRepository

    @Before
    fun createDatabases() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        sourceDatabase = Room.inMemoryDatabaseBuilder(context, WorkoutTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        targetDatabase = Room.inMemoryDatabaseBuilder(context, WorkoutTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sourcePrograms = programRepository(sourceDatabase)
        targetPrograms = programRepository(targetDatabase)
    }

    @After
    fun closeDatabases() {
        sourceDatabase.close()
        targetDatabase.close()
    }

    @Test
    fun roundTripReconstructsCompleteProgramWithoutHistoryAndKeepsItInactive() = runTest {
        val sourceProgramId = createCompleteSourceProgram()
        val existingExerciseId = targetDatabase.exerciseDao().insert(
            ExerciseEntity(name = "BENCH PRESS", createdAt = 1L),
        )
        val bytes = exportSourceProgram(sourceProgramId)
        val root = JSONObject(bytes.toString(Charsets.UTF_8))

        val imported = ProgramTransferRepository(targetDatabase)
            .importProgram(ByteArrayInputStream(bytes))

        assertEquals("workout-companion-program", root.getString("format"))
        assertEquals(2, root.getInt("formatVersion"))
        assertFalse(root.getJSONObject("program").has("id"))
        assertEquals("Transfer Plan", imported.name)
        assertNull(targetDatabase.programDao().observeActive().first())
        assertFalse(targetDatabase.programDao().getById(imported.id)!!.active)
        val days = targetDatabase.workoutTemplateDao().getForProgram(imported.id)
        assertEquals(listOf("Strength", "Conditioning"), days.map { it.name })
        assertEquals(listOf(0, 1), days.map { it.sortOrder })

        val strength = targetDatabase.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(days[0].id)
        val sourceStrength = sourceDatabase.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(sourceDatabase.workoutTemplateDao().getForProgram(sourceProgramId)[0].id)
        assertEquals(listOf("BENCH PRESS", "Machine Row"), strength.map { it.exerciseName })
        assertEquals(existingExerciseId, strength[0].exerciseId)
        assertEquals(TrackingMode.WEIGHT_REPS, strength[0].trackingMode)
        assertEquals(7_250, strength[0].currentWeightCentiKg)
        assertEquals(10, strength[0].currentTargetReps)
        assertEquals(250, strength[0].incrementCentiKg)
        assertEquals(180, strength[0].restSeconds)
        assertEquals("Rack 3", strength[0].setupNote)
        assertEquals(strength[0].supersetGroupId, strength[1].supersetGroupId)
        assertEquals(
            180,
            targetDatabase.supersetGroupDao().getById(strength[0].supersetGroupId!!)?.restSeconds,
        )
        val setTarget = targetDatabase.workoutTemplateSetTargetDao()
            .getForTemplateExercise(strength[0].id)
            .single()
        assertEquals(1, setTarget.setOrder)
        assertEquals(7_000, setTarget.prescribedWeightCentiKg)
        assertEquals(9, setTarget.prescribedReps)
        assertFalse(setTarget.countsForProgression)
        val warmups = targetDatabase.workoutTemplateWarmupSetDao()
            .getForTemplateExercise(strength[0].id)
        assertEquals(listOf(8, 5, 3), warmups.map { it.reps })
        assertEquals(listOf(WarmupLoadType.FIXED, WarmupLoadType.PERCENTAGE, WarmupLoadType.PERCENTAGE), warmups.map { it.loadType })
        assertEquals(listOf(2_000, null, null), warmups.map { it.fixedWeightCentiKg })
        assertEquals(listOf(null, 50, 70), warmups.map { it.percentOfWorkingWeight })
        assertEquals(125, strength[0].warmupRoundingCentiKg)
        assertNotEquals(sourceStrength[0].syncId, strength[0].syncId)
        assertNotEquals(sourceStrength[1].syncId, strength[1].syncId)

        val duration = targetDatabase.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(days[1].id)
            .single()
        assertEquals(TrackingMode.DURATION, duration.trackingMode)
        assertEquals(75, duration.targetDurationSeconds)
        assertEquals(15, duration.durationIncrementSeconds)
        assertEquals("Keep a steady pace", duration.setupNote)
        assertEquals(emptyList<Any>(), targetDatabase.workoutSessionDao().observeHistory().first())
    }

    @Test
    fun repeatedImportUsesSafeNamesAndIndependentProgression() = runTest {
        val bytes = exportSourceProgram(createCompleteSourceProgram())
        val first = ProgramTransferRepository(targetDatabase).importProgram(ByteArrayInputStream(bytes))
        targetPrograms.activateProgram(first.id)

        val second = ProgramTransferRepository(targetDatabase).importProgram(ByteArrayInputStream(bytes))

        assertEquals("Transfer Plan", first.name)
        assertEquals("Transfer Plan imported", second.name)
        assertEquals(first.id, targetDatabase.programDao().observeActive().first()?.id)
        val firstExerciseId = firstTemplateExerciseId(first.id)
        val secondExerciseId = firstTemplateExerciseId(second.id)
        assertNotEquals(firstExerciseId, secondExerciseId)
        val secondState = targetDatabase.progressionStateDao().getForTemplateExercise(secondExerciseId)!!
        targetDatabase.progressionStateDao().update(secondState.copy(currentTargetReps = 11))
        assertEquals(10, targetDatabase.progressionStateDao().getForTemplateExercise(firstExerciseId)?.currentTargetReps)
        assertEquals(11, targetDatabase.progressionStateDao().getForTemplateExercise(secondExerciseId)?.currentTargetReps)
        assertEquals(2, targetDatabase.programDao().getAll().size)
    }

    @Test
    fun invalidDocumentDoesNotCreatePartialProgram() = runTest {
        val validBytes = exportSourceProgram(createCompleteSourceProgram())
        val invalidText = validBytes.toString(Charsets.UTF_8)
            .replace("WEIGHT_REPS", "UNKNOWN_MODE", ignoreCase = false)

        try {
            ProgramTransferRepository(targetDatabase).importProgram(
                ByteArrayInputStream(invalidText.toByteArray()),
            )
            fail("Expected invalid program document to be rejected.")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }

        assertEquals(emptyList<Any>(), targetDatabase.programDao().getAll())
        assertEquals(emptyList<Any>(), targetDatabase.workoutTemplateDao().getForProgram(1L))
    }

    @Test
    fun roundTripPreservesFixedRepRange() = runTest {
        val sourceProgramId = createCompleteSourceProgram()
        val sourceExerciseId = firstSourceTemplateExerciseId(sourceProgramId)
        val sourceExercise = sourceDatabase.workoutTemplateExerciseDao().getById(sourceExerciseId)!!
        val editorItem = sourceDatabase.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(sourceExercise.workoutTemplateId)
            .first { it.id == sourceExerciseId }
        sourcePrograms.updateTemplateExercise(
            item = editorItem,
            config = weightedConfig().copy(
                repMin = 8,
                repMax = 8,
                currentTargetReps = 8,
            ),
            setupNote = editorItem.setupNote,
        )

        val imported = ProgramTransferRepository(targetDatabase).importProgram(
            ByteArrayInputStream(exportSourceProgram(sourceProgramId)),
        )
        val importedExerciseId = firstTemplateExerciseId(imported.id)
        val importedExercise = targetDatabase.workoutTemplateExerciseDao().getById(importedExerciseId)!!
        val importedProgression = targetDatabase.progressionStateDao()
            .getForTemplateExercise(importedExerciseId)!!

        assertEquals(8, importedExercise.repMin)
        assertEquals(8, importedExercise.repMax)
        assertEquals(8, importedProgression.currentTargetReps)
    }

    @Test
    fun legacyVersionOnePercentageWarmupsStillImport() = runTest {
        val programId = createCompleteSourceProgram()
        val sourceExerciseId = firstSourceTemplateExerciseId(programId)
        sourcePrograms.saveWarmupScheme(sourceExerciseId, 500, WarmupPreset.STANDARD.sets)
        val root = JSONObject(exportSourceProgram(programId).toString(Charsets.UTF_8))
        root.put("formatVersion", 1)
        val exercise = root.getJSONObject("program")
            .getJSONArray("trainingDays").getJSONObject(0)
            .getJSONArray("exercises").getJSONObject(0)
        exercise.remove("warmupRoundingCentiKg")
        val warmups = exercise.getJSONArray("warmupSets")
        for (index in 0 until warmups.length()) {
            val warmup = warmups.getJSONObject(index)
            warmup.remove("loadType")
            warmup.remove("fixedWeightCentiKg")
        }

        val imported = ProgramTransferRepository(targetDatabase).importProgram(
            ByteArrayInputStream(root.toString().toByteArray()),
        )
        val importedExerciseId = firstTemplateExerciseId(imported.id)
        val importedExercise = targetDatabase.workoutTemplateExerciseDao().getById(importedExerciseId)!!
        val importedWarmups = targetDatabase.workoutTemplateWarmupSetDao()
            .getForTemplateExercise(importedExerciseId)

        assertEquals(500, importedExercise.warmupRoundingCentiKg)
        assertEquals(listOf(50, 70, 85), importedWarmups.map { it.percentOfWorkingWeight })
        assertEquals(listOf(WarmupLoadType.PERCENTAGE, WarmupLoadType.PERCENTAGE, WarmupLoadType.PERCENTAGE), importedWarmups.map { it.loadType })
    }

    private suspend fun createCompleteSourceProgram(): Long {
        val programId = sourcePrograms.createProgram("Transfer Plan")
        val strengthId = sourcePrograms.createWorkoutTemplate(programId, "Strength")
        val benchId = sourcePrograms.createExerciseAndAddToWorkout(
            workoutTemplateId = strengthId,
            exerciseName = "Bench Press",
            config = weightedConfig(),
        )
        val rowId = sourcePrograms.createExerciseAndAddToWorkout(
            workoutTemplateId = strengthId,
            exerciseName = "Machine Row",
            config = weightedConfig().copy(currentWeightCentiKg = 6_000, restSeconds = 90),
        )
        sourcePrograms.supersetWithPrevious(strengthId, rowId)
        val benchItem = sourceDatabase.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(strengthId)
            .first { it.id == benchId }
        sourcePrograms.updateTemplateExercise(
            item = benchItem,
            config = weightedConfig(),
            setupNote = "Rack 3",
        )
        sourcePrograms.updateTemplateSetTarget(
            workoutTemplateExerciseId = benchId,
            setOrder = 1,
            prescribedWeightCentiKg = 7_000,
            prescribedReps = 9,
            countsForProgression = false,
        )
        sourcePrograms.saveWarmupScheme(
            benchId,
            roundingCentiKg = 125,
            sets = listOf(
                WarmupSetConfiguration(8, WarmupLoadType.FIXED, fixedWeightCentiKg = 2_000),
                WarmupSetConfiguration(5, WarmupLoadType.PERCENTAGE, percentOfWorkingWeight = 50),
                WarmupSetConfiguration(3, WarmupLoadType.PERCENTAGE, percentOfWorkingWeight = 70),
            ),
        )

        val conditioningId = sourcePrograms.createWorkoutTemplate(programId, "Conditioning")
        val durationId = sourcePrograms.createExerciseAndAddToWorkout(
            workoutTemplateId = conditioningId,
            exerciseName = "Plank",
            config = TemplateExerciseConfig(
                plannedWorkingSets = 2,
                repMin = 1,
                repMax = 1,
                incrementCentiKg = 250,
                restSeconds = 45,
                currentWeightCentiKg = 0,
                currentTargetReps = 1,
                trackingMode = TrackingMode.DURATION,
                targetDurationSeconds = 75,
                durationIncrementSeconds = 15,
            ),
        )
        val durationItem = sourceDatabase.workoutTemplateExerciseDao()
            .getEditorItemsForWorkoutTemplate(conditioningId)
            .first { it.id == durationId }
        sourcePrograms.updateTemplateExercise(
            item = durationItem,
            config = TemplateExerciseConfig(
                plannedWorkingSets = 2,
                repMin = 1,
                repMax = 1,
                incrementCentiKg = 250,
                restSeconds = 45,
                currentWeightCentiKg = 0,
                currentTargetReps = 1,
                trackingMode = TrackingMode.DURATION,
                targetDurationSeconds = 75,
                durationIncrementSeconds = 15,
            ),
            setupNote = "Keep a steady pace",
        )
        return programId
    }

    private fun weightedConfig() = TemplateExerciseConfig(
        plannedWorkingSets = 3,
        repMin = 8,
        repMax = 12,
        incrementCentiKg = 250,
        restSeconds = 180,
        currentWeightCentiKg = 7_250,
        currentTargetReps = 10,
    )

    private suspend fun exportSourceProgram(programId: Long): ByteArray {
        val output = ByteArrayOutputStream()
        ProgramTransferRepository(sourceDatabase).exportProgram(programId, output)
        return output.toByteArray()
    }

    private suspend fun firstTemplateExerciseId(programId: Long): Long {
        val template = targetDatabase.workoutTemplateDao().getForProgram(programId).first()
        return targetDatabase.workoutTemplateExerciseDao().getForWorkoutTemplate(template.id).first().id
    }

    private suspend fun firstSourceTemplateExerciseId(programId: Long): Long {
        val template = sourceDatabase.workoutTemplateDao().getForProgram(programId).first()
        return sourceDatabase.workoutTemplateExerciseDao().getForWorkoutTemplate(template.id).first().id
    }

    private fun programRepository(database: WorkoutTrackerDatabase) = ProgramRepository(
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
