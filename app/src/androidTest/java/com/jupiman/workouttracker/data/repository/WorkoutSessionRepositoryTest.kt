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
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateExerciseEntity
import com.jupiman.workouttracker.notification.RestTimerScheduler
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
    private lateinit var restTimerScheduler: FakeRestTimerScheduler

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WorkoutTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        restTimerScheduler = FakeRestTimerScheduler()
        repository = WorkoutSessionRepository(
            database = database,
            workoutSessionDao = database.workoutSessionDao(),
            sessionExerciseDao = database.sessionExerciseDao(),
            sessionSetDao = database.sessionSetDao(),
            programDao = database.programDao(),
            workoutTemplateDao = database.workoutTemplateDao(),
            workoutTemplateExerciseDao = database.workoutTemplateExerciseDao(),
            progressionStateDao = database.progressionStateDao(),
            supersetGroupDao = database.supersetGroupDao(),
            restTimerScheduler = restTimerScheduler,
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

    @Test
    fun completingWorkingSetStoresRestDeadlineAndSchedulesNotification() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val set = firstSessionSets(sessionId).first()

        repository.completeSet(set.id, actualWeightCentiKg = 7000, actualReps = 10)

        val session = database.workoutSessionDao().getById(sessionId)
        assertNotNull(session?.restEndsAt)
        assertEquals(session?.restEndsAt, restTimerScheduler.scheduledRestEndsAt)
    }

    @Test
    fun addRestTimeExtendsPersistedDeadlineAndReschedulesNotification() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        repository.completeSet(firstSessionSets(sessionId).first().id, actualWeightCentiKg = 7000, actualReps = 10)
        val originalRestEndsAt = database.workoutSessionDao().getById(sessionId)?.restEndsAt!!

        repository.addRestTime(30)

        val updatedRestEndsAt = database.workoutSessionDao().getById(sessionId)?.restEndsAt!!
        assertEquals(originalRestEndsAt + 30_000L, updatedRestEndsAt)
        assertEquals(updatedRestEndsAt, restTimerScheduler.scheduledRestEndsAt)
    }

    @Test
    fun skipRestClearsDeadlineAndCancelsNotification() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        repository.completeSet(firstSessionSets(sessionId).first().id, actualWeightCentiKg = 7000, actualReps = 10)

        repository.skipRest()

        assertNull(database.workoutSessionDao().getById(sessionId)?.restEndsAt)
        assertEquals(true, restTimerScheduler.cancelled)
    }

    @Test
    fun finishingWorkoutCancelsRestNotification() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        completeAllSets(sessionId, actualWeight = 7000, actualReps = 10)

        repository.finishActiveWorkout(allowPartial = false)

        assertNull(database.workoutSessionDao().getById(sessionId)?.restEndsAt)
        assertEquals(true, restTimerScheduler.cancelled)
    }

    @Test
    fun finishCompletedWorkoutAppliesSuccessfulProgression() = runTest {
        val templateId = seedBenchWorkout(targetReps = 10)
        val templateExerciseId = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single().id
        val sessionId = repository.startWorkout(templateId)
        completeAllSets(sessionId, actualWeight = 7000, actualReps = 10)

        repository.finishActiveWorkout(allowPartial = false)

        val session = database.workoutSessionDao().getById(sessionId)
        val progression = database.progressionStateDao().getForTemplateExercise(templateExerciseId)
        assertEquals(WorkoutSessionStatus.COMPLETED, session?.status)
        assertEquals(true, session?.progressionApplied)
        assertEquals(7000, progression?.currentWeightCentiKg)
        assertEquals(11, progression?.currentTargetReps)
    }

    @Test
    fun finishCompletedWorkoutAtRepMaxIncrementsWeightAndResetsReps() = runTest {
        val templateId = seedBenchWorkout(targetReps = 12)
        val templateExerciseId = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single().id
        repository.startWorkout(templateId).also { sessionId ->
            completeAllSets(sessionId, actualWeight = 7000, actualReps = 12)
        }

        repository.finishActiveWorkout(allowPartial = false)

        val progression = database.progressionStateDao().getForTemplateExercise(templateExerciseId)
        assertEquals(7250, progression?.currentWeightCentiKg)
        assertEquals(8, progression?.currentTargetReps)
    }

    @Test
    fun finishCompletedWorkoutWithFailedSetDoesNotProgress() = runTest {
        val templateId = seedBenchWorkout(targetReps = 10)
        val templateExerciseId = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single().id
        val sessionId = repository.startWorkout(templateId)
        val sets = firstSessionSets(sessionId)

        repository.completeSet(sets[0].id, actualWeightCentiKg = 7000, actualReps = 10)
        repository.completeSet(sets[1].id, actualWeightCentiKg = 7000, actualReps = 10)
        repository.completeSet(sets[2].id, actualWeightCentiKg = 7000, actualReps = 9)
        repository.finishActiveWorkout(allowPartial = false)

        val progression = database.progressionStateDao().getForTemplateExercise(templateExerciseId)
        assertEquals(7000, progression?.currentWeightCentiKg)
        assertEquals(10, progression?.currentTargetReps)
    }

    @Test
    fun finishPartialWorkoutAppliesOnlyCompletedExercises() = runTest {
        val seed = seedTwoExerciseWorkout()
        val sessionId = repository.startWorkout(seed.templateId)
        val sessionExercises = database.sessionExerciseDao().getForSession(sessionId)
        val firstExerciseSets = database.sessionSetDao().getForSessionExercise(sessionExercises[0].id)
        val secondExerciseSets = database.sessionSetDao().getForSessionExercise(sessionExercises[1].id)

        firstExerciseSets.forEach { set ->
            repository.completeSet(set.id, actualWeightCentiKg = 7000, actualReps = 10)
        }
        repository.completeSet(secondExerciseSets[0].id, actualWeightCentiKg = 5000, actualReps = 12)

        try {
            repository.finishActiveWorkout(allowPartial = false)
            fail("Expected incomplete workout to require partial confirmation.")
        } catch (expected: IllegalStateException) {
            assertEquals("Some planned sets are incomplete.", expected.message)
        }

        repository.finishActiveWorkout(allowPartial = true)

        val session = database.workoutSessionDao().getById(sessionId)
        val firstProgression = database.progressionStateDao().getForTemplateExercise(seed.firstTemplateExerciseId)
        val secondProgression = database.progressionStateDao().getForTemplateExercise(seed.secondTemplateExerciseId)
        assertEquals(WorkoutSessionStatus.PARTIAL, session?.status)
        assertEquals(11, firstProgression?.currentTargetReps)
        assertEquals(12, secondProgression?.currentTargetReps)
    }

    @Test
    fun skippedSetFinishesPartialAndDoesNotProgressThatExercise() = runTest {
        val templateId = seedBenchWorkout(targetReps = 10)
        val templateExerciseId = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single().id
        val sessionId = repository.startWorkout(templateId)
        val sets = firstSessionSets(sessionId)
        repository.completeSet(sets[0].id, actualWeightCentiKg = 7000, actualReps = 10)
        repository.completeSet(sets[1].id, actualWeightCentiKg = 7000, actualReps = 10)
        repository.skipSet(sets[2].id)

        repository.finishActiveWorkout(allowPartial = true)

        val session = database.workoutSessionDao().getById(sessionId)
        val progression = database.progressionStateDao().getForTemplateExercise(templateExerciseId)
        assertEquals(WorkoutSessionStatus.PARTIAL, session?.status)
        assertEquals(10, progression?.currentTargetReps)
    }

    @Test
    fun progressionCannotBeAppliedTwice() = runTest {
        val templateId = seedBenchWorkout(targetReps = 10)
        val templateExerciseId = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single().id
        val sessionId = repository.startWorkout(templateId)
        completeAllSets(sessionId, actualWeight = 7000, actualReps = 10)

        repository.finishActiveWorkout(allowPartial = false)

        try {
            repository.finishActiveWorkout(allowPartial = false)
            fail("Expected second finish to fail because there is no active workout.")
        } catch (expected: IllegalStateException) {
            assertEquals("No active workout to finish.", expected.message)
        }

        val session = database.workoutSessionDao().getById(sessionId)
        val progression = database.progressionStateDao().getForTemplateExercise(templateExerciseId)
        assertEquals(true, session?.progressionApplied)
        assertEquals(11, progression?.currentTargetReps)
    }

    private suspend fun completeAllSets(
        sessionId: Long,
        actualWeight: Int,
        actualReps: Int,
    ) {
        val sessionExercises = database.sessionExerciseDao().getForSession(sessionId)
        sessionExercises.forEach { sessionExercise ->
            database.sessionSetDao().getForSessionExercise(sessionExercise.id).forEach { set ->
                repository.completeSet(set.id, actualWeightCentiKg = actualWeight, actualReps = actualReps)
            }
        }
    }

    private suspend fun firstSessionSets(sessionId: Long) =
        database.sessionSetDao().getForSessionExercise(
            database.sessionExerciseDao().getForSession(sessionId).first().id,
        )

    private suspend fun seedBenchWorkout(targetReps: Int = 10): Long {
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
                currentTargetReps = targetReps,
                updatedAt = now,
            ),
        )
        return templateId
    }

    private suspend fun seedTwoExerciseWorkout(): TwoExerciseSeed {
        val now = 1_000L
        val programId = database.programDao().insert(
            ProgramEntity(name = "Current Program", active = true, createdAt = now),
        )
        val templateId = database.workoutTemplateDao().insert(
            WorkoutTemplateEntity(programId = programId, name = "Day A", sortOrder = 0),
        )
        val benchId = database.exerciseDao().insert(
            ExerciseEntity(name = "Bench Press", createdAt = now),
        )
        val rowId = database.exerciseDao().insert(
            ExerciseEntity(name = "Machine Row", createdAt = now),
        )
        val firstTemplateExerciseId = database.workoutTemplateExerciseDao().insert(
            WorkoutTemplateExerciseEntity(
                workoutTemplateId = templateId,
                exerciseId = benchId,
                sortOrder = 0,
                plannedWorkingSets = 3,
                repMin = 8,
                repMax = 12,
                incrementCentiKg = 250,
                restSeconds = 180,
            ),
        )
        val secondTemplateExerciseId = database.workoutTemplateExerciseDao().insert(
            WorkoutTemplateExerciseEntity(
                workoutTemplateId = templateId,
                exerciseId = rowId,
                sortOrder = 1,
                plannedWorkingSets = 3,
                repMin = 12,
                repMax = 15,
                incrementCentiKg = 250,
                restSeconds = 120,
            ),
        )
        database.progressionStateDao().insert(
            ProgressionStateEntity(
                workoutTemplateExerciseId = firstTemplateExerciseId,
                currentWeightCentiKg = 7000,
                currentTargetReps = 10,
                updatedAt = now,
            ),
        )
        database.progressionStateDao().insert(
            ProgressionStateEntity(
                workoutTemplateExerciseId = secondTemplateExerciseId,
                currentWeightCentiKg = 5000,
                currentTargetReps = 12,
                updatedAt = now,
            ),
        )
        return TwoExerciseSeed(
            templateId = templateId,
            firstTemplateExerciseId = firstTemplateExerciseId,
            secondTemplateExerciseId = secondTemplateExerciseId,
        )
    }

    private data class TwoExerciseSeed(
        val templateId: Long,
        val firstTemplateExerciseId: Long,
        val secondTemplateExerciseId: Long,
    )

    private class FakeRestTimerScheduler : RestTimerScheduler {
        var scheduledRestEndsAt: Long? = null
            private set
        var cancelled: Boolean = false
            private set

        override fun schedule(restEndsAt: Long) {
            scheduledRestEndsAt = restEndsAt
            cancelled = false
        }

        override fun cancel() {
            cancelled = true
        }
    }
}
