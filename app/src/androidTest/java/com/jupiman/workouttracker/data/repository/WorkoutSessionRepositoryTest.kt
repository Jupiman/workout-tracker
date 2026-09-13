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
import com.jupiman.workouttracker.data.local.entity.SupersetGroupEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateExerciseEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateWarmupSetEntity
import com.jupiman.workouttracker.notification.RestTimerScheduler
import com.jupiman.workouttracker.notification.WorkoutNotificationProjector
import com.jupiman.workouttracker.notification.WorkoutNotificationState
import com.jupiman.workouttracker.notification.WorkoutNotificationUpdater
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class WorkoutSessionRepositoryTest {
    private lateinit var database: WorkoutTrackerDatabase
    private lateinit var repository: WorkoutSessionRepository
    private lateinit var restTimerScheduler: FakeRestTimerScheduler
    private lateinit var workoutNotificationUpdater: FakeWorkoutNotificationUpdater

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WorkoutTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        restTimerScheduler = FakeRestTimerScheduler()
        workoutNotificationUpdater = FakeWorkoutNotificationUpdater()
        repository = WorkoutSessionRepository(
            database = database,
            workoutSessionDao = database.workoutSessionDao(),
            sessionExerciseDao = database.sessionExerciseDao(),
            sessionSetDao = database.sessionSetDao(),
            programDao = database.programDao(),
            workoutTemplateDao = database.workoutTemplateDao(),
            workoutTemplateExerciseDao = database.workoutTemplateExerciseDao(),
            workoutTemplateSetTargetDao = database.workoutTemplateSetTargetDao(),
            workoutTemplateWarmupSetDao = database.workoutTemplateWarmupSetDao(),
            progressionStateDao = database.progressionStateDao(),
            supersetGroupDao = database.supersetGroupDao(),
            restTimerScheduler = restTimerScheduler,
            workoutNotificationUpdater = workoutNotificationUpdater,
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
    fun startWorkoutGeneratesWarmupSetsBeforeWorkingSets() = runTest {
        val templateId = seedBenchWorkout()
        val templateExerciseId = database.workoutTemplateExerciseDao()
            .getForWorkoutTemplate(templateId)
            .single()
            .id
        database.workoutTemplateWarmupSetDao().insertAll(
            listOf(
                WorkoutTemplateWarmupSetEntity(
                    workoutTemplateExerciseId = templateExerciseId,
                    sortOrder = 0,
                    reps = 10,
                    percentOfWorkingWeight = 30,
                ),
                WorkoutTemplateWarmupSetEntity(
                    workoutTemplateExerciseId = templateExerciseId,
                    sortOrder = 1,
                    reps = 3,
                    percentOfWorkingWeight = 70,
                ),
                WorkoutTemplateWarmupSetEntity(
                    workoutTemplateExerciseId = templateExerciseId,
                    sortOrder = 2,
                    reps = 3,
                    percentOfWorkingWeight = 75,
                ),
            ),
        )

        val sessionId = repository.startWorkout(templateId)

        val sets = firstSessionSets(sessionId)
        assertEquals(
            listOf(SetType.WARMUP, SetType.WARMUP, SetType.WARMUP, SetType.WORKING, SetType.WORKING, SetType.WORKING),
            sets.map { it.setType },
        )
        assertEquals(listOf(-3, -2, -1, 0, 1, 2), sets.map { it.setOrder })
        assertEquals(listOf(2000, 5000, 5500), sets.take(3).map { it.prescribedWeightCentiKg })
        assertEquals(listOf(10, 3, 3), sets.take(3).map { it.prescribedReps })
        assertEquals(listOf(false, false, false), sets.take(3).map { it.countsForProgression })
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
    fun addSessionSetCreatesExtraSetWithDefaultsOutsideProgression() = runTest {
        val templateId = seedBenchWorkout(targetReps = 10)
        val templateExerciseId = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single().id
        val sessionId = repository.startWorkout(templateId)
        val sessionExercise = database.sessionExerciseDao().getForSession(sessionId).single()

        val extraSetId = repository.addSessionSet(sessionExercise.id, SetType.EXTRA)
        val extraSet = database.sessionSetDao().getById(extraSetId)!!

        assertEquals(3, extraSet.setOrder)
        assertEquals(SetType.EXTRA, extraSet.setType)
        assertEquals(false, extraSet.isPlanned)
        assertEquals(false, extraSet.countsForProgression)
        assertEquals(7000, extraSet.prescribedWeightCentiKg)
        assertEquals(10, extraSet.prescribedReps)

        completeAllSets(sessionId, actualWeight = 7000, actualReps = 10)
        repository.completeSet(extraSetId, actualWeightCentiKg = 7000, actualReps = 1)
        repository.finishActiveWorkout(allowPartial = false)

        val progression = database.progressionStateDao().getForTemplateExercise(templateExerciseId)
        assertEquals(11, progression?.currentTargetReps)
    }

    @Test
    fun addSessionSetCreatesAmrapSetWithWeightAndBlankReps() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val sessionExercise = database.sessionExerciseDao().getForSession(sessionId).single()

        val amrapSetId = repository.addSessionSet(sessionExercise.id, SetType.AMRAP)
        val amrapSet = database.sessionSetDao().getById(amrapSetId)!!

        assertEquals(SetType.AMRAP, amrapSet.setType)
        assertEquals(false, amrapSet.isPlanned)
        assertEquals(false, amrapSet.countsForProgression)
        assertEquals(7000, amrapSet.prescribedWeightCentiKg)
        assertNull(amrapSet.prescribedReps)
    }

    @Test
    fun addSessionSetCreatesDropSetFromPreviousActualWeight() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val sessionExercise = database.sessionExerciseDao().getForSession(sessionId).single()
        val plannedSets = database.sessionSetDao().getForSessionExercise(sessionExercise.id)
        repository.completeSet(plannedSets.last().id, actualWeightCentiKg = 6500, actualReps = 8)

        val dropSetId = repository.addSessionSet(sessionExercise.id, SetType.DROP)
        val dropSet = database.sessionSetDao().getById(dropSetId)!!

        assertEquals(SetType.DROP, dropSet.setType)
        assertEquals(false, dropSet.isPlanned)
        assertEquals(false, dropSet.countsForProgression)
        assertEquals(6500, dropSet.prescribedWeightCentiKg)
        assertNull(dropSet.prescribedReps)
    }

    @Test
    fun supersetRestsOnlyAfterLastExerciseInGroup() = runTest {
        val seed = seedTwoExerciseWorkout()
        val groupId = database.supersetGroupDao().insert(
            SupersetGroupEntity(
                workoutTemplateId = seed.templateId,
                restSeconds = 90,
            ),
        )
        val firstTemplateExercise = database.workoutTemplateExerciseDao()
            .getById(seed.firstTemplateExerciseId)!!
        val secondTemplateExercise = database.workoutTemplateExerciseDao()
            .getById(seed.secondTemplateExerciseId)!!
        database.workoutTemplateExerciseDao().update(firstTemplateExercise.copy(supersetGroupId = groupId))
        database.workoutTemplateExerciseDao().update(secondTemplateExercise.copy(supersetGroupId = groupId))
        val sessionId = repository.startWorkout(seed.templateId)
        val sessionExercises = database.sessionExerciseDao().getForSession(sessionId)
        val firstExerciseSets = database.sessionSetDao().getForSessionExercise(sessionExercises[0].id)
        val secondExerciseSets = database.sessionSetDao().getForSessionExercise(sessionExercises[1].id)

        repository.completeSet(firstExerciseSets[0].id, actualWeightCentiKg = 7000, actualReps = 10)

        assertNull(database.workoutSessionDao().getById(sessionId)?.restEndsAt)
        assertEquals(true, restTimerScheduler.cancelled)

        val before = System.currentTimeMillis()
        repository.completeSet(secondExerciseSets[0].id, actualWeightCentiKg = 5000, actualReps = 12)
        val after = System.currentTimeMillis()

        val restEndsAt = database.workoutSessionDao().getById(sessionId)?.restEndsAt
        assertNotNull(restEndsAt)
        assertEquals(restEndsAt, restTimerScheduler.scheduledRestEndsAt)
        assertTrue(restEndsAt!! >= before + 90_000L)
        assertTrue(restEndsAt <= after + 90_000L)
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
    fun activeWorkoutStateSurvivesRepositoryRecreation() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val firstSet = firstSessionSets(sessionId).first()
        repository.completeSet(firstSet.id, actualWeightCentiKg = 7000, actualReps = 10)

        val restartedScheduler = FakeRestTimerScheduler()
        val restartedRepository = WorkoutSessionRepository(
            database = database,
            workoutSessionDao = database.workoutSessionDao(),
            sessionExerciseDao = database.sessionExerciseDao(),
            sessionSetDao = database.sessionSetDao(),
            programDao = database.programDao(),
            workoutTemplateDao = database.workoutTemplateDao(),
            workoutTemplateExerciseDao = database.workoutTemplateExerciseDao(),
            workoutTemplateSetTargetDao = database.workoutTemplateSetTargetDao(),
            workoutTemplateWarmupSetDao = database.workoutTemplateWarmupSetDao(),
            progressionStateDao = database.progressionStateDao(),
            supersetGroupDao = database.supersetGroupDao(),
            restTimerScheduler = restartedScheduler,
        )

        val activeWorkout = restartedRepository.activeSessionWithDetails.first()!!
        val activeSets = activeWorkout.exercises.single().sets.sortedBy { it.setOrder }

        assertEquals(sessionId, activeWorkout.session.id)
        assertNotNull(activeWorkout.session.restEndsAt)
        assertEquals(SessionSetStatus.COMPLETED, activeSets[0].status)
        assertEquals(SessionSetStatus.PENDING, activeSets[1].status)
        assertEquals(SessionSetStatus.PENDING, activeSets[2].status)

        restartedRepository.syncRestTimerAlarm()

        assertEquals(activeWorkout.session.restEndsAt, restartedScheduler.scheduledRestEndsAt)
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
    fun activeWorkoutProjectsCurrentSetNotificationState() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val activeWorkout = database.workoutSessionDao().getActiveWithDetails()

        val state = WorkoutNotificationProjector.stateFor(activeWorkout)

        val setState = state as WorkoutNotificationState.SetAction
        assertEquals(sessionId, setState.sessionId)
        assertEquals(firstSessionSets(sessionId).first().id, setState.setId)
        assertEquals("Bench Press", setState.title)
        assertEquals("Set 1/3 • 70 kg x 10", setState.text)
    }

    @Test
    fun notificationCompleteSetIsIdempotentForExplicitSet() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val firstSetId = firstSessionSets(sessionId).first().id

        assertEquals(true, repository.completeSetFromNotification(sessionId, firstSetId))
        assertEquals(false, repository.completeSetFromNotification(sessionId, firstSetId))

        val sessionExercise = database.sessionExerciseDao().getForSession(sessionId).single()
        val sets = database.sessionSetDao().getForSessionExercise(sessionExercise.id)
        assertEquals(SessionSetStatus.COMPLETED, sets.first { it.id == firstSetId }.status)
        assertEquals(
            listOf(SessionSetStatus.COMPLETED, SessionSetStatus.PENDING, SessionSetStatus.PENDING),
            sets.sortedBy { it.setOrder }.map { it.status },
        )
    }

    @Test
    fun restStateCanBeReconstructedFromPersistedDeadline() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val firstSetId = firstSessionSets(sessionId).first().id
        repository.completeSetFromNotification(sessionId, firstSetId)
        val activeWorkout = database.workoutSessionDao().getActiveWithDetails()

        val state = WorkoutNotificationProjector.stateFor(
            activeWorkout = activeWorkout,
            now = System.currentTimeMillis(),
        )

        val restState = state as WorkoutNotificationState.Resting
        assertEquals(database.workoutSessionDao().getById(sessionId)?.restEndsAt, restState.restEndsAt)
        assertEquals("Rest", restState.title)
        assertEquals("Next: Bench Press • 70 kg x 10", restState.text)
    }

    @Test
    fun notificationActionAgainstFinishedWorkoutDoesNothingSafely() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val firstSetId = firstSessionSets(sessionId).first().id
        completeAllSets(sessionId, actualWeight = 7000, actualReps = 10)
        repository.finishActiveWorkout(allowPartial = false)

        assertEquals(false, repository.completeSetFromNotification(sessionId, firstSetId))
        assertEquals(true, workoutNotificationUpdater.cancelled)
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
    fun finishedWorkoutAppearsInHistoryWithDetails() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val sessionExercise = database.sessionExerciseDao().getForSession(sessionId).single()
        val sets = database.sessionSetDao().getForSessionExercise(sessionExercise.id)
        repository.completeSet(sets[0].id, actualWeightCentiKg = 7000, actualReps = 10)
        repository.completeSet(sets[1].id, actualWeightCentiKg = 7000, actualReps = 10)
        repository.completeSet(sets[2].id, actualWeightCentiKg = 7000, actualReps = 9)

        repository.finishActiveWorkout(allowPartial = false)

        val history = database.workoutSessionDao().observeHistoryWithDetails().first()
        val historicalSession = history.single()
        val historicalExercise = historicalSession.exercises.single()
        val historicalSets = historicalExercise.sets.sortedBy { it.setOrder }

        assertEquals(sessionId, historicalSession.session.id)
        assertEquals(WorkoutSessionStatus.COMPLETED, historicalSession.session.status)
        assertEquals("Day A", historicalSession.session.workoutNameSnapshot)
        assertEquals("Bench Press", historicalExercise.exercise.exerciseNameSnapshot)
        assertEquals(listOf(10, 10, 9), historicalSets.map { it.actualReps })
    }

    @Test
    fun historyDetailsRemainSnapshotsAfterSourceEdits() = runTest {
        val templateId = seedBenchWorkout()
        val templateExercise = database.workoutTemplateExerciseDao()
            .getForWorkoutTemplate(templateId)
            .single()
        val exercise = database.exerciseDao().getById(templateExercise.exerciseId)!!
        val template = database.workoutTemplateDao().getById(templateId)!!
        val sessionId = repository.startWorkout(templateId)
        completeAllSets(sessionId, actualWeight = 7000, actualReps = 10)
        repository.finishActiveWorkout(allowPartial = false)

        database.exerciseDao().update(exercise.copy(name = "Barbell Bench Press"))
        database.workoutTemplateDao().update(template.copy(name = "Renamed Day"))
        database.workoutTemplateExerciseDao().update(
            templateExercise.copy(
                plannedWorkingSets = 1,
                repMin = 3,
                repMax = 5,
                restSeconds = 30,
            ),
        )

        val historicalSession = database.workoutSessionDao()
            .observeHistoryWithDetails()
            .first()
            .single()
        val historicalExercise = historicalSession.exercises.single().exercise

        assertEquals("Day A", historicalSession.session.workoutNameSnapshot)
        assertEquals("Bench Press", historicalExercise.exerciseNameSnapshot)
        assertEquals(3, historicalExercise.plannedSetCountSnapshot)
        assertEquals(8, historicalExercise.repMinSnapshot)
        assertEquals(12, historicalExercise.repMaxSnapshot)
        assertEquals(180, historicalExercise.restSecondsSnapshot)
    }

    @Test
    fun historySurvivesSourceArchivingAndTemplateDeletion() = runTest {
        val templateId = seedBenchWorkout()
        val template = database.workoutTemplateDao().getById(templateId)!!
        val templateExercise = database.workoutTemplateExerciseDao()
            .getForWorkoutTemplate(templateId)
            .single()
        val sessionId = repository.startWorkout(templateId)
        completeAllSets(sessionId, actualWeight = 7000, actualReps = 10)
        repository.finishActiveWorkout(allowPartial = false)

        database.exerciseDao().archive(templateExercise.exerciseId)
        database.workoutTemplateDao().deleteById(templateId)
        database.programDao().archive(template.programId)

        val historicalSession = database.workoutSessionDao()
            .observeHistoryWithDetails()
            .first()
            .single()

        assertEquals(sessionId, historicalSession.session.id)
        assertEquals("Day A", historicalSession.session.workoutNameSnapshot)
        assertEquals("Bench Press", historicalSession.exercises.single().exercise.exerciseNameSnapshot)
        assertEquals(3, historicalSession.exercises.single().sets.size)
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
    fun finishWorkoutWithChangedSetDefaultsToNoChange() = runTest {
        val templateId = seedBenchWorkout(targetReps = 10)
        val templateExerciseId = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single().id
        val sessionId = repository.startWorkout(templateId)
        val sets = firstSessionSets(sessionId)
        repository.completeSet(sets[0].id, actualWeightCentiKg = 7250, actualReps = 9)
        repository.completeSet(sets[1].id, actualWeightCentiKg = 7000, actualReps = 10)
        repository.completeSet(sets[2].id, actualWeightCentiKg = 7000, actualReps = 10)

        repository.finishActiveWorkout(allowPartial = false)

        val progression = database.progressionStateDao().getForTemplateExercise(templateExerciseId)
        val setTargets = database.workoutTemplateSetTargetDao().getForTemplateExercise(templateExerciseId)
        assertEquals(7000, progression?.currentWeightCentiKg)
        assertEquals(10, progression?.currentTargetReps)
        assertEquals(emptyList<Long>(), setTargets.map { it.id })
    }

    @Test
    fun finishWorkoutCanChangeOnlyOneSetTarget() = runTest {
        val templateId = seedBenchWorkout(targetReps = 10)
        val templateExerciseId = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single().id
        val sessionId = repository.startWorkout(templateId)
        val sets = firstSessionSets(sessionId)
        repository.completeSet(sets[0].id, actualWeightCentiKg = 7250, actualReps = 9)
        repository.completeSet(sets[1].id, actualWeightCentiKg = 7000, actualReps = 10)
        repository.completeSet(sets[2].id, actualWeightCentiKg = 7000, actualReps = 10)

        repository.finishActiveWorkout(
            allowPartial = false,
            progressionChoices = mapOf(sets[0].id to ProgressionFinishChoice.CHANGE_THIS_SET),
        )

        val progression = database.progressionStateDao().getForTemplateExercise(templateExerciseId)
        val setTargets = database.workoutTemplateSetTargetDao().getForTemplateExercise(templateExerciseId)
        assertEquals(7000, progression?.currentWeightCentiKg)
        assertEquals(10, progression?.currentTargetReps)
        assertEquals(1, setTargets.size)
        assertEquals(0, setTargets.single().setOrder)
        assertEquals(7250, setTargets.single().prescribedWeightCentiKg)
        assertEquals(9, setTargets.single().prescribedReps)

        val nextSessionId = repository.startWorkout(templateId)
        val nextSets = firstSessionSets(nextSessionId)
        assertEquals(7250, nextSets[0].prescribedWeightCentiKg)
        assertEquals(9, nextSets[0].prescribedReps)
        assertEquals(7000, nextSets[1].prescribedWeightCentiKg)
        assertEquals(10, nextSets[1].prescribedReps)
    }

    @Test
    fun finishWorkoutCanSetLoggedTargetForAllExerciseSets() = runTest {
        val templateId = seedBenchWorkout(targetReps = 12)
        val templateExerciseId = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single().id
        val sessionId = repository.startWorkout(templateId)
        val sets = firstSessionSets(sessionId)
        sets.forEach { set ->
            repository.completeSet(set.id, actualWeightCentiKg = 7250, actualReps = 14)
        }

        repository.finishActiveWorkout(
            allowPartial = false,
            progressionChoices = mapOf(sets[0].id to ProgressionFinishChoice.SET_TARGET_FOR_EXERCISE),
        )

        val progression = database.progressionStateDao().getForTemplateExercise(templateExerciseId)
        val setTargets = database.workoutTemplateSetTargetDao().getForTemplateExercise(templateExerciseId)
        assertEquals(7250, progression?.currentWeightCentiKg)
        assertEquals(12, progression?.currentTargetReps)
        assertEquals(emptyList<Long>(), setTargets.map { it.id })

        val nextSessionId = repository.startWorkout(templateId)
        val nextSets = firstSessionSets(nextSessionId)
        assertEquals(listOf(7250, 7250, 7250), nextSets.map { it.prescribedWeightCentiKg })
        assertEquals(listOf(12, 12, 12), nextSets.map { it.prescribedReps })
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
    fun finishPartialWorkoutStoresPendingSetsAsSkippedInHistory() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout(targetReps = 10))
        val sets = firstSessionSets(sessionId)
        repository.completeSet(sets[0].id, actualWeightCentiKg = 7000, actualReps = 10)

        repository.finishActiveWorkout(allowPartial = true)

        val historicalSets = database.workoutSessionDao()
            .observeHistoryWithDetails()
            .first()
            .single()
            .exercises
            .single()
            .sets
            .sortedBy { it.setOrder }

        assertEquals(WorkoutSessionStatus.PARTIAL, database.workoutSessionDao().getById(sessionId)?.status)
        assertEquals(SessionSetStatus.COMPLETED, historicalSets[0].status)
        assertEquals(SessionSetStatus.SKIPPED, historicalSets[1].status)
        assertEquals(SessionSetStatus.SKIPPED, historicalSets[2].status)
        assertNull(historicalSets[1].actualWeightCentiKg)
        assertNull(historicalSets[1].actualReps)
        assertNull(historicalSets[2].actualWeightCentiKg)
        assertNull(historicalSets[2].actualReps)
    }

    @Test
    fun finishWorkoutMarksPendingWarmupsSkippedWithoutPartialStatus() = runTest {
        val templateId = seedBenchWorkout(targetReps = 10)
        val templateExerciseId = database.workoutTemplateExerciseDao()
            .getForWorkoutTemplate(templateId)
            .single()
            .id
        database.workoutTemplateWarmupSetDao().insertAll(
            listOf(
                WorkoutTemplateWarmupSetEntity(
                    workoutTemplateExerciseId = templateExerciseId,
                    sortOrder = 0,
                    reps = 10,
                    percentOfWorkingWeight = 30,
                ),
            ),
        )
        val sessionId = repository.startWorkout(templateId)
        val sets = firstSessionSets(sessionId)

        sets.filter { it.setType == SetType.WORKING }.forEach { set ->
            repository.completeSet(set.id, actualWeightCentiKg = 7000, actualReps = 10)
        }

        repository.finishActiveWorkout(allowPartial = false)

        val session = database.workoutSessionDao().getById(sessionId)
        val historicalSets = database.workoutSessionDao()
            .observeHistoryWithDetails()
            .first()
            .single()
            .exercises
            .single()
            .sets
        assertEquals(WorkoutSessionStatus.COMPLETED, session?.status)
        assertEquals(SessionSetStatus.SKIPPED, historicalSets.first { it.setType == SetType.WARMUP }.status)
        assertEquals(
            listOf(SessionSetStatus.COMPLETED, SessionSetStatus.COMPLETED, SessionSetStatus.COMPLETED),
            historicalSets.filter { it.setType == SetType.WORKING }.map { it.status },
        )
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

    private class FakeWorkoutNotificationUpdater : WorkoutNotificationUpdater {
        var refreshCount: Int = 0
            private set
        var cancelled: Boolean = false
            private set

        override fun refresh() {
            refreshCount += 1
        }

        override fun cancel() {
            cancelled = true
        }
    }
}
