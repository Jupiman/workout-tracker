package com.jupiman.workouttracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import com.jupiman.workouttracker.data.local.entity.ProgressionStateEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateExerciseEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class WorkoutSessionRepositoryTest {
    private lateinit var database: WorkoutTrackerDatabase
    private lateinit var repository: WorkoutSessionRepository

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WorkoutTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkoutSessionRepository(
            database = database,
            workoutSessionDao = database.workoutSessionDao(),
            sessionExerciseDao = database.sessionExerciseDao(),
            sessionSetDao = database.sessionSetDao(),
            programDao = database.programDao(),
            workoutTemplateDao = database.workoutTemplateDao(),
            workoutTemplateExerciseDao = database.workoutTemplateExerciseDao(),
            supersetGroupDao = database.supersetGroupDao(),
        )
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun startWorkoutSnapshotsTemplateProgressionAndPlannedSets() = runTest {
        val templateId = seedBenchWorkout()

        val sessionId = repository.startWorkout(templateId)

        val session = database.workoutSessionDao().getById(sessionId)
        val sessionExercise = database.sessionExerciseDao().getForSession(sessionId).single()
        val sets = database.sessionSetDao().getForSessionExercise(sessionExercise.id)

        assertNotNull(session)
        assertEquals("Current Program", session?.programNameSnapshot)
        assertEquals("Day A", session?.workoutNameSnapshot)
        assertEquals("Bench Press", sessionExercise.exerciseNameSnapshot)
        assertEquals(3, sessionExercise.plannedSetCountSnapshot)
        assertEquals(8, sessionExercise.repMinSnapshot)
        assertEquals(12, sessionExercise.repMaxSnapshot)
        assertEquals(10, sessionExercise.targetRepsSnapshot)
        assertEquals(7000, sessionExercise.prescribedWeightCentiKgSnapshot)
        assertEquals(250, sessionExercise.incrementCentiKgSnapshot)
        assertEquals(180, sessionExercise.restSecondsSnapshot)
        assertEquals(listOf(0, 1, 2), sets.map { it.setOrder })
        sets.forEach { set ->
            assertEquals(SetType.WORKING, set.setType)
            assertEquals(true, set.isPlanned)
            assertEquals(true, set.countsForProgression)
            assertEquals(7000, set.prescribedWeightCentiKg)
            assertEquals(10, set.prescribedReps)
            assertEquals(SessionSetStatus.PENDING, set.status)
            assertNull(set.actualWeightCentiKg)
            assertNull(set.actualReps)
        }
    }

    @Test
    fun startWorkoutPreservesSnapshotAfterSourceEdits() = runTest {
        val templateId = seedBenchWorkout()
        val sessionId = repository.startWorkout(templateId)
        val exercise = database.exerciseDao().getByName("Bench Press")!!
        val template = database.workoutTemplateDao().getById(templateId)!!

        database.exerciseDao().update(exercise.copy(name = "Barbell Bench Press"))
        database.workoutTemplateDao().update(template.copy(name = "Renamed Day"))

        val session = database.workoutSessionDao().getById(sessionId)
        val sessionExercise = database.sessionExerciseDao().getForSession(sessionId).single()

        assertEquals("Day A", session?.workoutNameSnapshot)
        assertEquals("Bench Press", sessionExercise.exerciseNameSnapshot)
    }

    @Test
    fun cannotStartSecondWorkoutWhileActiveSessionExists() = runTest {
        val templateId = seedBenchWorkout()

        repository.startWorkout(templateId)

        try {
            repository.startWorkout(templateId)
            fail("Expected second start to fail while an active session exists.")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Finish or discard the active workout first.", expected.message)
        }
    }

    @Test
    fun setLoggingPersistsCompleteEditPendingAndSkippedStates() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val sessionExercise = database.sessionExerciseDao().getForSession(sessionId).single()
        val set = database.sessionSetDao().getForSessionExercise(sessionExercise.id).first()

        repository.completeSet(set.id, actualWeightCentiKg = 7000, actualReps = 10)
        val completed = database.sessionSetDao().getById(set.id)!!
        assertEquals(SessionSetStatus.COMPLETED, completed.status)
        assertEquals(7000, completed.actualWeightCentiKg)
        assertEquals(10, completed.actualReps)
        assertNotNull(completed.completedAt)

        repository.completeSet(set.id, actualWeightCentiKg = 6750, actualReps = 8)
        val edited = database.sessionSetDao().getById(set.id)!!
        assertEquals(SessionSetStatus.COMPLETED, edited.status)
        assertEquals(6750, edited.actualWeightCentiKg)
        assertEquals(8, edited.actualReps)

        repository.uncompleteSet(set.id)
        val pending = database.sessionSetDao().getById(set.id)!!
        assertEquals(SessionSetStatus.PENDING, pending.status)
        assertNull(pending.actualWeightCentiKg)
        assertNull(pending.actualReps)
        assertNull(pending.completedAt)

        repository.skipSet(set.id)
        val skipped = database.sessionSetDao().getById(set.id)!!
        assertEquals(SessionSetStatus.SKIPPED, skipped.status)
        assertNull(skipped.actualWeightCentiKg)
        assertNull(skipped.actualReps)
        assertNotNull(skipped.completedAt)
    }

    private suspend fun seedBenchWorkout(): Long {
        val now = 1_000L
        val programId = database.programDao().insert(
            ProgramEntity(name = "Current Program", active = true, createdAt = now),
        )
        val templateId = database.workoutTemplateDao().insert(
            WorkoutTemplateEntity(programId = programId, name = "Day A", sortOrder = 0),
        )
        val exerciseId = database.exerciseDao().insert(
            ExerciseEntity(name = "Bench Press", createdAt = now),
        )
        val templateExerciseId = database.workoutTemplateExerciseDao().insert(
            WorkoutTemplateExerciseEntity(
                workoutTemplateId = templateId,
                exerciseId = exerciseId,
                sortOrder = 0,
                plannedWorkingSets = 3,
                repMin = 8,
                repMax = 12,
                incrementCentiKg = 250,
                restSeconds = 180,
            ),
        )
        database.progressionStateDao().insert(
            ProgressionStateEntity(
                workoutTemplateExerciseId = templateExerciseId,
                currentWeightCentiKg = 7000,
                currentTargetReps = 10,
                updatedAt = now,
            ),
        )
        return templateId
    }
}
