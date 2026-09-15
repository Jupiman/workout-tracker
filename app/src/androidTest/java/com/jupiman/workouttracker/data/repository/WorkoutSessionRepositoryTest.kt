package com.jupiman.workouttracker.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import com.jupiman.workouttracker.data.local.entity.ExerciseEntity
import com.jupiman.workouttracker.data.local.entity.TrackingMode
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
import com.jupiman.workouttracker.notification.DurationTimerScheduler
import com.jupiman.workouttracker.notification.WorkoutNotificationProjector
import com.jupiman.workouttracker.notification.WorkoutNotificationState
import com.jupiman.workouttracker.notification.WorkoutNotificationUpdater
import com.jupiman.workouttracker.preferences.DurationPreparationProvider
import com.jupiman.workouttracker.wear.WorkoutWearStateProjector
import com.jupiman.workouttracker.wearprotocol.WearSessionStatus
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
    private lateinit var durationTimerScheduler: FakeDurationTimerScheduler
    private lateinit var workoutNotificationUpdater: FakeWorkoutNotificationUpdater

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WorkoutTrackerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        restTimerScheduler = FakeRestTimerScheduler()
        durationTimerScheduler = FakeDurationTimerScheduler()
        workoutNotificationUpdater = FakeWorkoutNotificationUpdater()
        repository = createRepository()
    }

    private fun createRepository(
        durationPreparationProvider: DurationPreparationProvider = DurationPreparationProvider { 3 },
    ) = WorkoutSessionRepository(
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
            durationTimerScheduler = durationTimerScheduler,
            workoutNotificationUpdater = workoutNotificationUpdater,
            durationPreparationProvider = durationPreparationProvider,
        )

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
    fun replaceExerciseForTodayUpdatesOnlySessionAndDoesNotProgressOriginalTrack() = runTest {
        val templateId = seedBenchWorkout(targetReps = 10)
        val templateExerciseId = database.workoutTemplateExerciseDao()
            .getForWorkoutTemplate(templateId)
            .single()
            .id
        val sessionId = repository.startWorkout(templateId)
        val sessionExercise = database.sessionExerciseDao().getForSession(sessionId).single()

        repository.replaceExerciseForToday(
            sessionExerciseId = sessionExercise.id,
            replacementExerciseName = "Dumbbell Press",
        )

        val replacedExercise = database.sessionExerciseDao().getById(sessionExercise.id)
        val replacedSets = database.sessionSetDao().getForSessionExercise(sessionExercise.id)
        assertEquals("Dumbbell Press", replacedExercise?.exerciseNameSnapshot)
        assertNull(replacedExercise?.sourceWorkoutTemplateExerciseId)
        assertEquals("", replacedExercise?.setupNoteSnapshot)
        assertEquals(listOf(false, false, false), replacedSets.map { it.countsForProgression })
        assertEquals(listOf(7000, 7000, 7000), replacedSets.map { it.prescribedWeightCentiKg })
        assertEquals(listOf(10, 10, 10), replacedSets.map { it.prescribedReps })

        completeAllSets(sessionId, actualWeight = 7000, actualReps = 10)
        repository.finishActiveWorkout(allowPartial = false)

        val historyExercise = database.sessionExerciseDao()
            .getForSession(sessionId)
            .single()
        val originalProgression = database.progressionStateDao().getForTemplateExercise(templateExerciseId)
        assertEquals("Dumbbell Press", historyExercise?.exerciseNameSnapshot)
        assertNull(historyExercise?.sourceWorkoutTemplateExerciseId)
        assertEquals(10, originalProgression?.currentTargetReps)
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
            durationTimerScheduler = FakeDurationTimerScheduler(),
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
    fun activeWorkoutProjectsWearStateFromCurrentSet() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val activeWorkout = database.workoutSessionDao().getActiveWithDetails()

        val state = WorkoutWearStateProjector.stateFor(activeWorkout, now = 5_000L)

        assertEquals(WearSessionStatus.ACTIVE, state.sessionStatus)
        assertEquals(sessionId, state.sessionId)
        assertEquals(firstSessionSets(sessionId).first().id, state.currentSetId)
        assertEquals("Bench Press", state.exerciseName)
        assertEquals(7000, state.weightCentiKg)
        assertEquals(10, state.targetReps)
        assertEquals("Set 1/3", state.setLabel)
        assertEquals(1, state.setNumber)
        assertEquals(3, state.totalSets)
    }

    @Test
    fun wearCompleteSetIsIdempotentForCurrentSet() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val firstSetId = firstSessionSets(sessionId).first().id

        assertEquals(true, repository.completeSetFromWearCommand(sessionId, firstSetId))
        assertEquals(false, repository.completeSetFromWearCommand(sessionId, firstSetId))

        val sets = firstSessionSets(sessionId).sortedBy { it.setOrder }
        assertEquals(
            listOf(SessionSetStatus.COMPLETED, SessionSetStatus.PENDING, SessionSetStatus.PENDING),
            sets.map { it.status },
        )
    }

    @Test
    fun staleWearCommandForNonCurrentPendingSetDoesNothingSafely() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val sets = firstSessionSets(sessionId).sortedBy { it.setOrder }

        assertEquals(false, repository.completeSetFromWearCommand(sessionId, sets[1].id))

        assertEquals(
            listOf(SessionSetStatus.PENDING, SessionSetStatus.PENDING, SessionSetStatus.PENDING),
            firstSessionSets(sessionId).sortedBy { it.setOrder }.map { it.status },
        )
    }

    @Test
    fun wearRestStateContainsRestDeadlineAndNextSet() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        repository.completeSetFromWearCommand(sessionId, firstSessionSets(sessionId).first().id)
        val activeWorkout = database.workoutSessionDao().getActiveWithDetails()

        val state = WorkoutWearStateProjector.stateFor(activeWorkout)

        assertEquals(WearSessionStatus.ACTIVE, state.sessionStatus)
        assertEquals(firstSessionSets(sessionId).sortedBy { it.setOrder }[1].id, state.currentSetId)
        assertEquals(database.workoutSessionDao().getById(sessionId)?.restEndsAt, state.restEndsAt)
    }

    @Test
    fun wearSupersetStateFollowsPhoneSequencing() = runTest {
        val seed = seedTwoExerciseWorkout()
        val groupId = database.supersetGroupDao().insert(
            SupersetGroupEntity(
                workoutTemplateId = seed.templateId,
                restSeconds = 90,
            ),
        )
        database.workoutTemplateExerciseDao().update(
            database.workoutTemplateExerciseDao().getById(seed.firstTemplateExerciseId)!!.copy(supersetGroupId = groupId),
        )
        database.workoutTemplateExerciseDao().update(
            database.workoutTemplateExerciseDao().getById(seed.secondTemplateExerciseId)!!.copy(supersetGroupId = groupId),
        )
        val sessionId = repository.startWorkout(seed.templateId)
        val sessionExercises = database.sessionExerciseDao().getForSession(sessionId)
        val benchSets = database.sessionSetDao().getForSessionExercise(sessionExercises[0].id)
        val rowSets = database.sessionSetDao().getForSessionExercise(sessionExercises[1].id)

        var state = WorkoutWearStateProjector.stateFor(database.workoutSessionDao().getActiveWithDetails())
        assertEquals(benchSets[0].id, state.currentSetId)
        assertEquals(1, state.supersetPosition)
        assertEquals(2, state.supersetSize)

        repository.completeSetFromWearCommand(sessionId, benchSets[0].id)
        state = WorkoutWearStateProjector.stateFor(database.workoutSessionDao().getActiveWithDetails())
        assertEquals(rowSets[0].id, state.currentSetId)
        assertEquals(2, state.supersetPosition)

        repository.completeSetFromWearCommand(sessionId, rowSets[0].id)
        state = WorkoutWearStateProjector.stateFor(database.workoutSessionDao().getActiveWithDetails())
        assertEquals(benchSets[1].id, state.currentSetId)
        assertEquals(1, state.supersetPosition)
        assertNotNull(state.restEndsAt)
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

    @Test
    fun skippingLastSupersetMemberStillRestsAfterRemainingRound() = runTest {
        val seed = seedTwoExerciseWorkout()
        val groupId = database.supersetGroupDao().insert(SupersetGroupEntity(workoutTemplateId = seed.templateId, restSeconds = 90))
        database.workoutTemplateExerciseDao().getForWorkoutTemplate(seed.templateId).forEach {
            database.workoutTemplateExerciseDao().update(it.copy(supersetGroupId = groupId))
        }
        val sessionId = repository.startWorkout(seed.templateId)
        val exercises = database.sessionExerciseDao().getForSession(sessionId)
        repository.skipExercise(exercises.last().id)
        val set = database.sessionSetDao().getForSessionExercise(exercises.first().id).first()
        val undo = repository.completeSet(set.id, 7000, 10)!!
        assertNotNull(database.workoutSessionDao().getActive()!!.restEndsAt)
        assertTrue(repository.undoCompletion(undo!!))
        assertNull(database.workoutSessionDao().getActive()!!.restEndsAt)
        assertTrue(database.sessionSetDao().getForSessionExercise(exercises.last().id).all { it.status == SessionSetStatus.SKIPPED })
    }

    @Test
    fun invalidSessionExerciseConfigurationDoesNotInsertAnything() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val id = database.exerciseDao().getByName("Bench Press")!!.id
        val before = database.workoutSessionDao().getActiveWithDetails()
        val invalid = listOf(listOf(0, 8, 0, 30), listOf(1, 0, 0, 30), listOf(1, 8, -1, 30), listOf(1, 8, 0, -1))
        invalid.forEach { config ->
            try {
                repository.addExerciseForToday(sessionId, id, config[0], config[1], config[2], config[3])
                fail("Expected validation failure")
            } catch (_: IllegalArgumentException) { }
            assertEquals(before, database.workoutSessionDao().getActiveWithDetails())
        }
    }

    @Test
    fun skipExercisePreservesLoggedAndSkippedSetsAndBlocksProgression() = runTest {
        val templateId = seedBenchWorkout()
        val template = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single()
        val progression = database.progressionStateDao().getForTemplateExercise(template.id)
        database.workoutTemplateWarmupSetDao().insertAll(listOf(
            WorkoutTemplateWarmupSetEntity(workoutTemplateExerciseId = template.id, sortOrder = 0, reps = 5, percentOfWorkingWeight = 50),
        ))
        val sessionId = repository.startWorkout(templateId)
        val exercise = database.sessionExerciseDao().getForSession(sessionId).single()
        val sets = firstSessionSets(sessionId)
        repository.completeSet(sets[1].id, 7000, 10)
        repository.skipSet(sets[2].id)
        val completed = database.sessionSetDao().getById(sets[1].id)
        val skipped = database.sessionSetDao().getById(sets[2].id)
        repository.skipExercise(exercise.id)
        repository.skipExercise(exercise.id)
        assertEquals(completed, database.sessionSetDao().getById(sets[1].id))
        assertEquals(skipped, database.sessionSetDao().getById(sets[2].id))
        assertEquals(listOf(SessionSetStatus.SKIPPED, SessionSetStatus.COMPLETED, SessionSetStatus.SKIPPED, SessionSetStatus.SKIPPED), firstSessionSets(sessionId).map { it.status })
        repository.finishActiveWorkout(true)
        assertEquals(progression, database.progressionStateDao().getForTemplateExercise(template.id))
        assertEquals(template, database.workoutTemplateExerciseDao().getById(template.id))
        val history = repository.historyWithDetails.first().single()
        assertEquals(3, history.exercises.single().sets.count { it.status == SessionSetStatus.SKIPPED })
        assertEquals(WorkoutSessionStatus.PARTIAL, history.session.status)
    }

    @Test
    fun addForTodayHasNoProgressionTrackAndSnapshotsHistory() = runTest {
        val templateId = seedBenchWorkout()
        val templatesBefore = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId)
        val original = database.progressionStateDao().getForTemplateExercise(templatesBefore.single().id)
        val sessionId = repository.startWorkout(templateId)
        val library = database.exerciseDao().getByName("Bench Press")!!
        val addedId = repository.addExerciseForToday(sessionId, library.id, 2, 15, 4000, 60)
        val added = database.sessionExerciseDao().getById(addedId)!!
        assertNull(added.sourceWorkoutTemplateExerciseId)
        assertNull(added.supersetGroupSnapshot)
        database.sessionSetDao().getForSessionExercise(addedId).forEach {
            assertTrue(!it.countsForProgression)
            repository.completeSet(it.id, 4250, 16)
        }
        repository.skipExercise(database.sessionExerciseDao().getForSession(sessionId).first().id)
        repository.finishActiveWorkout(true)
        database.exerciseDao().update(library.copy(name = "Renamed"))
        val history = repository.historyWithDetails.first().single().exercises.first { it.exercise.id == addedId }
        assertEquals("Bench Press", history.exercise.exerciseNameSnapshot)
        assertTrue(history.sets.all { it.actualWeightCentiKg == 4250 && it.actualReps == 16 })
        assertEquals(original, database.progressionStateDao().getForTemplateExercise(templatesBefore.single().id))
        assertEquals(templatesBefore, database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId))
        repository.startWorkout(templateId)
        assertEquals(1, database.workoutSessionDao().getActiveWithDetails()!!.exercises.size)
    }

    @Test
    fun doLaterMovesWholeSupersetAndWearRejectsOldCurrentSet() = runTest {
        val seed = seedTwoExerciseWorkout()
        val groupId = database.supersetGroupDao().insert(SupersetGroupEntity(workoutTemplateId = seed.templateId, restSeconds = 90))
        val originals = database.workoutTemplateExerciseDao().getForWorkoutTemplate(seed.templateId)
        originals.forEach { database.workoutTemplateExerciseDao().update(it.copy(supersetGroupId = groupId)) }
        val sessionId = repository.startWorkout(seed.templateId)
        val group = database.sessionExerciseDao().getForSession(sessionId)
        val oldCurrent = WorkoutWearStateProjector.stateFor(database.workoutSessionDao().getActiveWithDetails()).currentSetId!!
        val libraryId = database.exerciseDao().getByName("Bench Press")!!.id
        val addedId = repository.addExerciseForToday(sessionId, libraryId, 1, 8, 1000, 30)
        repository.doExerciseLater(group.first().id)
        val reordered = database.sessionExerciseDao().getForSession(sessionId)
        assertEquals(listOf(addedId) + group.map { it.id }, reordered.map { it.id })
        assertEquals(listOf(0, 1, 2), reordered.map { it.sortOrderSnapshot })
        assertEquals(listOf(groupId, groupId), reordered.drop(1).map { it.supersetGroupSnapshot })
        val workout = database.workoutSessionDao().getActiveWithDetails()!!
        assertEquals(WorkoutNotificationProjector.nextActionableSet(workout)?.setId, WorkoutWearStateProjector.stateFor(workout).currentSetId)
        assertTrue(!repository.completeSetFromWearCommand(sessionId, oldCurrent))
        assertEquals(originals.map { it.sortOrder }, database.workoutTemplateExerciseDao().getForWorkoutTemplate(seed.templateId).map { it.sortOrder })
    }

    @Test
    fun sessionEditsSurviveDatabaseCloseAndReopen() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "phase-one-${System.nanoTime()}.db"
        database.close()
        try {
            database = Room.databaseBuilder(context, WorkoutTrackerDatabase::class.java, name).allowMainThreadQueries().build()
            repository = createRepository()
            val seed = seedTwoExerciseWorkout()
            val sessionId = repository.startWorkout(seed.templateId)
            val original = database.sessionExerciseDao().getForSession(sessionId)
            val addedId = repository.addExerciseForToday(sessionId, database.exerciseDao().getByName("Bench Press")!!.id, 2, 9, 3000, 45)
            repository.doExerciseLater(original.first().id)
            repository.skipExercise(original.last().id)
            val expected = database.sessionExerciseDao().getForSession(sessionId)
            database.close()
            database = Room.databaseBuilder(context, WorkoutTrackerDatabase::class.java, name).allowMainThreadQueries().build()
            repository = createRepository()
            assertEquals(expected, database.sessionExerciseDao().getForSession(sessionId))
            val workout = database.workoutSessionDao().getActiveWithDetails()!!
            assertTrue(workout.exercises.first { it.exercise.id == original.last().id }.sets.all { it.status == SessionSetStatus.SKIPPED })
            val added = workout.exercises.first { it.exercise.id == addedId }
            assertEquals(2, added.sets.size)
            assertTrue(added.sets.none { it.countsForProgression })
            assertEquals(added.sets.minBy { it.setOrder }.id, WorkoutWearStateProjector.stateFor(workout).currentSetId)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun undoRestoresEditableValuesAndCancelsOnlyItsOwnRest() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val set = firstSessionSets(sessionId).first()
        val undo = repository.completeSet(set.id, 6750, 9)!!
        assertTrue(repository.undoCompletion(undo))
        val pending = database.sessionSetDao().getById(set.id)!!
        assertEquals(SessionSetStatus.PENDING, pending.status)
        assertEquals(6750, pending.actualWeightCentiKg)
        assertEquals(9, pending.actualReps)
        assertNull(pending.completedAt)
        assertNull(database.workoutSessionDao().getActive()!!.restEndsAt)
        assertTrue(restTimerScheduler.cancelled)
        assertTrue(!repository.undoCompletion(undo))
    }

    @Test
    fun undoCannotRevertSubsequentPhoneOrWearCompletion() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val sets = firstSessionSets(sessionId)
        val firstUndo = repository.completeSet(sets[0].id, 7000, 10)!!
        val secondUndo = repository.completeSet(sets[1].id, 7000, 10)!!
        assertTrue(!repository.undoCompletion(firstUndo))
        assertTrue(repository.completeSetFromWearCommand(sessionId, sets[2].id))
        val rest = database.workoutSessionDao().getActive()!!.restEndsAt
        assertTrue(!repository.undoCompletion(secondUndo))
        assertEquals(rest, database.workoutSessionDao().getActive()!!.restEndsAt)
        assertTrue(firstSessionSets(sessionId).all { it.status == SessionSetStatus.COMPLETED })
    }

    @Test
    fun undoPreservesExtendedRestAndCompletedEditsInvalidateReceipt() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val set = firstSessionSets(sessionId).first()
        val undo = repository.completeSet(set.id, 7000, 10)!!
        repository.addRestTime(30)
        val rest = database.workoutSessionDao().getActive()!!.restEndsAt
        assertTrue(repository.undoCompletion(undo))
        assertEquals(rest, database.workoutSessionDao().getActive()!!.restEndsAt)
        assertTrue(!restTimerScheduler.cancelled)
        val nextUndo = repository.completeSet(set.id, 7000, 10)!!
        assertNull(repository.completeSet(set.id, 7000, 11))
        assertTrue(!repository.undoCompletion(nextUndo))
        assertEquals(11, database.sessionSetDao().getById(set.id)!!.actualReps)
    }

    @Test
    fun warmupUndoLeavesEarlierRestAlone() = runTest {
        val templateId = seedBenchWorkout()
        val templateExercise = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single()
        database.workoutTemplateWarmupSetDao().insertAll(listOf(
            WorkoutTemplateWarmupSetEntity(workoutTemplateExerciseId = templateExercise.id, sortOrder = 0, reps = 5, percentOfWorkingWeight = 50),
        ))
        val sessionId = repository.startWorkout(templateId)
        val sets = firstSessionSets(sessionId)
        repository.completeSet(sets[1].id, 7000, 10)
        val rest = database.workoutSessionDao().getActive()!!.restEndsAt
        val undo = repository.completeSet(sets[0].id, 3500, 5)!!
        assertTrue(repository.undoCompletion(undo))
        assertEquals(rest, database.workoutSessionDao().getActive()!!.restEndsAt)
    }

    @Test
    fun finalizedHistoryRejectsSessionActionsAndUndo() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val exercise = database.sessionExerciseDao().getForSession(sessionId).single()
        val set = firstSessionSets(sessionId).first()
        val undo = repository.completeSet(set.id, 7000, 10)!!
        repository.finishActiveWorkout(true)
        val before = repository.historyWithDetails.first()
        val library = database.exerciseDao().getByName("Bench Press")!!
        val actions: List<suspend () -> Unit> = listOf(
            { repository.skipExercise(exercise.id) },
            { repository.doExerciseLater(exercise.id) },
            { repository.addExerciseForToday(sessionId, library.id, 1, 8, 0, 0) },
            { repository.uncompleteSet(set.id) },
            { repository.skipSet(set.id) },
        )
        actions.forEach { action ->
            try { action(); fail("Expected active-session guard") } catch (_: IllegalArgumentException) { }
        }
        assertTrue(!repository.undoCompletion(undo))
        assertEquals(before, repository.historyWithDetails.first())
    }

    @Test
    fun wearFinishUsesExistingProgressionAndRejectsDuplicateOrDifferentSession() = runTest {
        val templateId = seedBenchWorkout()
        val sessionId = repository.startWorkout(templateId)
        firstSessionSets(sessionId).forEach { repository.completeSet(it.id, 7000, 10) }
        repository.finishActiveWorkout(false, expectedWearSessionId = sessionId)
        val exercise = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single()
        val progressed = database.progressionStateDao().getForTemplateExercise(exercise.id)
        assertEquals(11, progressed!!.currentTargetReps)
        assertEquals(WorkoutSessionStatus.COMPLETED, database.workoutSessionDao().getById(sessionId)!!.status)
        assertTrue(restTimerScheduler.cancelled)
        try {
            repository.finishActiveWorkout(false, expectedWearSessionId = sessionId)
            fail("Duplicate finish must fail")
        } catch (_: IllegalStateException) { }
        val nextId = repository.startWorkout(templateId)
        firstSessionSets(nextId).forEach { repository.completeSet(it.id, 7000, 11) }
        try {
            repository.finishActiveWorkout(false, expectedWearSessionId = sessionId)
            fail("Stale finish must fail")
        } catch (_: IllegalArgumentException) { }
        assertEquals(nextId, database.workoutSessionDao().getActive()!!.id)
        assertEquals(progressed, database.progressionStateDao().getForTemplateExercise(exercise.id))
    }

    @Test
    fun wearFinishPreservesPendingPartialAndChangedTargetReview() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val sets = firstSessionSets(sessionId)
        suspend fun assertRejected() {
            val before = database.workoutSessionDao().getActiveWithDetails()
            try {
                repository.finishActiveWorkout(false, expectedWearSessionId = sessionId)
                fail("Must finish on phone")
            } catch (_: IllegalArgumentException) { }
            assertEquals(before, database.workoutSessionDao().getActiveWithDetails())
        }
        assertRejected()
        sets.forEach { repository.skipSet(it.id) }
        assertRejected()
        sets.forEach { repository.completeSet(it.id, 6750, 10) }
        assertRejected()
    }

    @Test
    fun completionSummaryReportsCommittedWeightProgressionAndDuration() = runTest {
        val templateId = seedBenchWorkout(targetReps = 12)
        val sessionId = repository.startWorkout(templateId)
        val active = database.workoutSessionDao().getActive()!!
        database.workoutSessionDao().update(active.copy(startedAt = System.currentTimeMillis() - 57 * 60_000L))
        firstSessionSets(sessionId).forEach { repository.completeSet(it.id, 7000, 12) }
        val summary = repository.finishActiveWorkout(false)
        assertEquals("Day A", summary.workoutName)
        assertTrue(summary.durationSeconds in 3420..3430)
        assertEquals(3, summary.completedWorkingSets)
        assertEquals(3, summary.totalWorkingSets)
        assertEquals(0, summary.skippedSets)
        assertTrue(!summary.partial)
        val result = summary.exercises.single()
        assertEquals(List(3) { CompletionTarget(7000, 12) }, result.before)
        assertEquals(List(3) { CompletionTarget(7250, 8) }, result.after)
        val source = database.sessionExerciseDao().getForSession(sessionId).single().sourceWorkoutTemplateExerciseId!!
        val stored = database.progressionStateDao().getForTemplateExercise(source)!!
        assertEquals(CompletionTarget(stored.currentWeightCentiKg, stored.currentTargetReps), result.after!!.first())
        assertEquals(summary, repository.completionSummary.value)
    }

    @Test
    fun completionSummaryCountsPartialWorkingSetsAndAllSkippedSets() = runTest {
        val templateId = seedBenchWorkout()
        val source = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single().id
        database.workoutTemplateWarmupSetDao().insertAll(listOf(
            WorkoutTemplateWarmupSetEntity(workoutTemplateExerciseId = source, sortOrder = 0, reps = 5, percentOfWorkingWeight = 50),
        ))
        val sessionId = repository.startWorkout(templateId)
        val exerciseId = database.sessionExerciseDao().getForSession(sessionId).single().id
        repository.completeSet(firstSessionSets(sessionId).first { it.setType == SetType.WORKING }.id, 7000, 10)
        val extra = repository.addSessionSet(exerciseId, SetType.EXTRA)
        repository.completeSet(extra, 7000, 10)
        val summary = repository.finishActiveWorkout(true)
        assertTrue(summary.partial)
        assertEquals(1, summary.completedWorkingSets)
        assertEquals(3, summary.totalWorkingSets)
        assertEquals(3, summary.skippedSets)
        assertEquals(summary.exercises.single().before, summary.exercises.single().after)
        val history = repository.historyWithDetails.first().single()
        assertEquals(summary.skippedSets, history.exercises.flatMap { it.sets }.count { it.status == SessionSetStatus.SKIPPED })
    }

    @Test
    fun completionSummaryReflectsExplicitPerSetTargetsWithoutRecalculating() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val sets = firstSessionSets(sessionId)
        sets.forEachIndexed { index, set -> repository.completeSet(set.id, if (index == 1) 6500 else 7000, 10) }
        val summary = repository.finishActiveWorkout(false, mapOf(sets[1].id to ProgressionFinishChoice.CHANGE_THIS_SET))
        assertEquals(listOf(CompletionTarget(7000, 10), CompletionTarget(6500, 10), CompletionTarget(7000, 10)), summary.exercises.single().after)
        val source = database.sessionExerciseDao().getForSession(sessionId).single().sourceWorkoutTemplateExerciseId!!
        assertEquals(6500, database.workoutTemplateSetTargetDao().getForTemplateExercise(source).single().prescribedWeightCentiKg)
    }

    @Test
    fun completionSummaryReportsWholeExerciseDecisionAndNoChangeAccurately() = runTest {
        val templateId = seedBenchWorkout()
        val sessionId = repository.startWorkout(templateId)
        val sets = firstSessionSets(sessionId)
        sets.forEach { repository.completeSet(it.id, 6750, 9) }
        val summary = repository.finishActiveWorkout(false, mapOf(sets.first().id to ProgressionFinishChoice.SET_TARGET_FOR_EXERCISE))
        assertEquals(List(3) { CompletionTarget(6750, 9) }, summary.exercises.single().after)
        val next = repository.startWorkout(templateId)
        assertNull(repository.completionSummary.value)
        firstSessionSets(next).forEach { repository.completeSet(it.id, 6500, 8) }
        val unchanged = repository.finishActiveWorkout(false)
        assertEquals(unchanged.exercises.single().before, unchanged.exercises.single().after)
        assertEquals(List(3) { CompletionTarget(6750, 9) }, unchanged.exercises.single().after)
    }

    @Test
    fun summaryForWearFinishExcludesSessionOnlyProgressionAndDismissDoesNotWriteData() = runTest {
        val sessionId = repository.startWorkout(seedBenchWorkout())
        val source = database.sessionExerciseDao().getForSession(sessionId).single()
        repository.replaceExerciseForToday(source.id, "Replacement")
        val addedId = repository.addExerciseForToday(sessionId, database.exerciseDao().getByName("Bench Press")!!.id, 1, 8, 0, 0)
        database.sessionSetDao().getForSessionExercise(source.id).forEach { repository.completeSet(it.id, 7000, 10) }
        database.sessionSetDao().getForSessionExercise(addedId).forEach { repository.completeSet(it.id, 0, 8) }
        val summary = repository.finishActiveWorkout(false, expectedWearSessionId = sessionId)
        assertEquals(4, summary.completedWorkingSets)
        assertTrue(summary.exercises.all { it.before == null && it.after == null })
        val history = repository.historyWithDetails.first()
        repository.dismissCompletionSummary(sessionId + 1)
        assertEquals(summary, repository.completionSummary.value)
        repository.dismissCompletionSummary(sessionId)
        assertNull(repository.completionSummary.value)
        assertEquals(history, repository.historyWithDetails.first())
    }

    @Test
    fun summaryCapturesActualFutureTargetsAndIsUnaffectedByLaterProgramEdits() = runTest {
        val templateId = seedBenchWorkout()
        val sessionId = repository.startWorkout(templateId)
        val sourceId = database.sessionExerciseDao().getForSession(sessionId).single().sourceWorkoutTemplateExerciseId!!
        val progression = database.progressionStateDao().getForTemplateExercise(sourceId)!!
        database.progressionStateDao().update(progression.copy(currentWeightCentiKg = 8000))
        repository.skipExercise(database.sessionExerciseDao().getForSession(sessionId).single().id)
        val summary = repository.finishActiveWorkout(true)
        assertEquals(List(3) { CompletionTarget(8000, 10) }, summary.exercises.single().before)
        assertEquals(summary.exercises.single().before, summary.exercises.single().after)
        database.progressionStateDao().update(progression.copy(currentWeightCentiKg = 9000))
        assertEquals(summary, repository.completionSummary.value)
        assertEquals(List(3) { CompletionTarget(8000, 10) }, repository.completionSummary.value!!.exercises.single().after)
    }

    @Test
    fun backupRoundTripPreservesHistoryActiveWorkoutAndProgression() = runTest {
        val templateId = seedBenchWorkout()
        val finishedId = repository.startWorkout(templateId)
        firstSessionSets(finishedId).forEach { repository.completeSet(it.id, 7000, 10) }
        repository.finishActiveWorkout(false)
        val activeId = repository.startWorkout(templateId)
        repository.completeSet(firstSessionSets(activeId).first().id, 7000, 11)
        val activeBefore = database.workoutSessionDao().getActiveWithDetails()
        val historyBefore = repository.historyWithDetails.first()
        val templatesBefore = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId)
        val progressionBefore = database.progressionStateDao().getForTemplateExercise(templatesBefore.single().id)
        val output = java.io.ByteArrayOutputStream()
        val backup = DataBackupRepository(database)
        backup.exportBackup(output)
        repository.discardActiveWorkout()
        backup.restoreBackup(java.io.ByteArrayInputStream(output.toByteArray()))
        repository.syncRestTimerAlarm()
        assertEquals(activeBefore, database.workoutSessionDao().getActiveWithDetails())
        assertEquals(historyBefore, repository.historyWithDetails.first())
        assertEquals(templatesBefore, database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId))
        assertEquals(progressionBefore, database.progressionStateDao().getForTemplateExercise(templatesBefore.single().id))
        assertEquals(activeBefore!!.session.restEndsAt, restTimerScheduler.scheduledRestEndsAt)
    }

    @Test
    fun durationLogsSecondsAndProgressesOnceWithImmutableHistoryAndBackup() = runTest {
        val templateId = seedBenchWorkout()
        val template = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single()
        database.workoutTemplateExerciseDao().update(template.copy(
            trackingMode = TrackingMode.DURATION, targetDurationSeconds = 60, durationIncrementSeconds = 5,
        ))
        val sessionId = repository.startWorkout(templateId)
        val snapshot = database.sessionExerciseDao().getForSession(sessionId).single()
        assertEquals(TrackingMode.DURATION, snapshot.trackingModeSnapshot)
        assertEquals(5, snapshot.durationIncrementSecondsSnapshot)
        val state = WorkoutNotificationProjector.nextActionableSet(database.workoutSessionDao().getActiveWithDetails()!!)!!
        assertEquals("60 sec", state.targetText)
        val wear = WorkoutWearStateProjector.stateFor(database.workoutSessionDao().getActiveWithDetails())
        assertEquals(com.jupiman.workouttracker.wearprotocol.WearTrackingMode.DURATION, wear.trackingMode)
        assertEquals(60, wear.targetDurationSeconds)
        val sets = firstSessionSets(sessionId)
        assertTrue(sets.all { it.prescribedReps == null && it.prescribedWeightCentiKg == null && it.prescribedDurationSeconds == 60 })
        val undo = repository.completeSet(sets.first().id, 9999, 99, 65)
        assertTrue(repository.undoCompletion(undo!!))
        sets.forEach { repository.completeSet(it.id, 9999, 99, 65) }
        val summary = repository.finishActiveWorkout(false)
        assertEquals(60, summary.exercises.single().before!!.first().durationSeconds)
        assertEquals(65, summary.exercises.single().after!!.first().durationSeconds)
        val logged = database.sessionSetDao().getForSessionExercise(snapshot.id)
        assertTrue(logged.all { it.actualDurationSeconds == 65 && it.actualReps == null && it.actualWeightCentiKg == null })
        val output = java.io.ByteArrayOutputStream()
        DataBackupRepository(database).exportBackup(output)
        DataBackupRepository(database).restoreBackup(java.io.ByteArrayInputStream(output.toByteArray()))
        assertEquals(logged, database.sessionSetDao().getForSessionExercise(snapshot.id))
        assertEquals(65, database.workoutTemplateExerciseDao().getById(template.id)!!.targetDurationSeconds)
        val next = repository.startWorkout(templateId)
        assertEquals(65, firstSessionSets(next).first().prescribedDurationSeconds)
        assertEquals(60, database.sessionExerciseDao().getById(snapshot.id)!!.targetDurationSecondsSnapshot)
    }

    @Test
    fun durationMissOrSkippedSetHoldsTargetAndDoesNotOverwriteProgramEdits() = runTest {
        val templateId = seedBenchWorkout()
        val template = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single()
        val duration = template.copy(trackingMode = TrackingMode.DURATION, targetDurationSeconds = 60, durationIncrementSeconds = 5)
        database.workoutTemplateExerciseDao().update(duration)
        val first = firstSessionSets(repository.startWorkout(templateId))
        first.forEachIndexed { index, set -> repository.completeSet(set.id, 0, 0, if (index == 0) 59 else 60) }
        repository.finishActiveWorkout(false)
        assertEquals(60, database.workoutTemplateExerciseDao().getById(template.id)!!.targetDurationSeconds)
        val second = firstSessionSets(repository.startWorkout(templateId))
        repository.skipSet(second.first().id)
        second.drop(1).forEach { repository.completeSet(it.id, 0, 0, 60) }
        repository.finishActiveWorkout(true)
        assertEquals(60, database.workoutTemplateExerciseDao().getById(template.id)!!.targetDurationSeconds)
        firstSessionSets(repository.startWorkout(templateId)).forEach { repository.completeSet(it.id, 0, 0, 60) }
        database.workoutTemplateExerciseDao().update(duration.copy(targetDurationSeconds = 90))
        repository.finishActiveWorkout(false)
        assertEquals(90, database.workoutTemplateExerciseDao().getById(template.id)!!.targetDurationSeconds)
    }

    @Test
    fun repsOnlyProgressesToCapWithoutWeightAndSessionOnlyDurationHasNoProgramSource() = runTest {
        val templateId = seedBenchWorkout(targetReps = 11)
        val template = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single()
        database.workoutTemplateExerciseDao().update(template.copy(trackingMode = TrackingMode.REPS))
        val progression = database.progressionStateDao().getForTemplateExercise(template.id)!!
        database.progressionStateDao().update(progression.copy(currentWeightCentiKg = 0))
        firstSessionSets(repository.startWorkout(templateId)).forEach { repository.completeSet(it.id, 9999, 11) }
        repository.finishActiveWorkout(false)
        assertEquals(12, database.progressionStateDao().getForTemplateExercise(template.id)!!.currentTargetReps)
        val sessionId = repository.startWorkout(templateId)
        firstSessionSets(sessionId).forEach { repository.completeSet(it.id, 9999, 12) }
        val added = repository.addExerciseForToday(sessionId, template.exerciseId, 1, 1, 0, 30, TrackingMode.DURATION, 45)
        val addedSnapshot = database.sessionExerciseDao().getById(added)!!
        assertNull(addedSnapshot.sourceWorkoutTemplateExerciseId)
        val addedSet = database.sessionSetDao().getForSessionExercise(added).single()
        assertEquals(45, addedSet.prescribedDurationSeconds)
        assertNull(addedSet.prescribedReps)
        repository.completeSet(addedSet.id, 0, 0, 45)
        repository.finishActiveWorkout(false)
        val final = database.progressionStateDao().getForTemplateExercise(template.id)!!
        assertEquals(12, final.currentTargetReps)
        assertEquals(0, final.currentWeightCentiKg)
    }

    @Test
    fun legacyBackupRestoresExistingWeightedWorkoutWithDefaults() = runTest {
        val templateId = seedBenchWorkout()
        val sessionId = repository.startWorkout(templateId)
        repository.completeSet(firstSessionSets(sessionId).first().id, 7000, 10)
        val output = java.io.ByteArrayOutputStream()
        DataBackupRepository(database).exportBackup(output)
        val backup = org.json.JSONObject(output.toString("UTF-8"))
            .put("formatVersion", 1).put("schemaVersion", 5)
        val tables = backup.getJSONObject("tables")
        mapOf(
            "workout_template_exercises" to listOf("trackingMode", "targetDurationSeconds", "durationIncrementSeconds"),
            "session_exercises" to listOf("trackingModeSnapshot", "targetDurationSecondsSnapshot", "durationIncrementSecondsSnapshot"),
            "session_sets" to listOf("prescribedDurationSeconds", "actualDurationSeconds"),
        ).forEach { (table, columns) ->
            val rows = tables.getJSONArray(table)
            repeat(rows.length()) { index -> columns.forEach { rows.getJSONObject(index).remove(it) } }
        }
        DataBackupRepository(database).restoreBackup(java.io.ByteArrayInputStream(backup.toString().toByteArray()))
        val exercise = database.sessionExerciseDao().getForSession(sessionId).single()
        assertEquals(TrackingMode.WEIGHT_REPS, exercise.trackingModeSnapshot)
        assertNull(exercise.targetDurationSecondsSnapshot)
        assertEquals(0, exercise.durationIncrementSecondsSnapshot)
        assertEquals(7000, firstSessionSets(sessionId).first().actualWeightCentiKg)
        assertEquals(10, firstSessionSets(sessionId).first().actualReps)
    }

    @Test
    fun initialTrackingBackupPreservesDurationAndDefaultsIncrementToZero() = runTest {
        val templateId = seedBenchWorkout()
        val template = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single()
        database.workoutTemplateExerciseDao().update(template.copy(trackingMode = TrackingMode.DURATION, targetDurationSeconds = 45))
        val sessionId = repository.startWorkout(templateId)
        val output = java.io.ByteArrayOutputStream()
        DataBackupRepository(database).exportBackup(output)
        val backup = org.json.JSONObject(output.toString("UTF-8")).put("formatVersion", 2).put("schemaVersion", 6)
        val tables = backup.getJSONObject("tables")
        tables.getJSONArray("workout_template_exercises").getJSONObject(0).remove("durationIncrementSeconds")
        tables.getJSONArray("session_exercises").getJSONObject(0).remove("durationIncrementSecondsSnapshot")
        DataBackupRepository(database).restoreBackup(java.io.ByteArrayInputStream(backup.toString().toByteArray()))
        val restored = database.workoutTemplateExerciseDao().getById(template.id)!!
        assertEquals(TrackingMode.DURATION, restored.trackingMode)
        assertEquals(45, restored.targetDurationSeconds)
        assertEquals(0, restored.durationIncrementSeconds)
        assertEquals(45, firstSessionSets(sessionId).first().prescribedDurationSeconds)
    }

    @Test
    fun durationStartPersistsExactOwnerAndDeadlinesAndClearsRest() = runTest {
        val sessionId = repository.startWorkout(seedDurationWorkout(60))
        val setId = firstSessionSets(sessionId).first().id
        database.workoutSessionDao().update(database.workoutSessionDao().getById(sessionId)!!.copy(restEndsAt = 9_999L))

        assertTrue(repository.startDurationSet(sessionId, setId, now = 1_000L))

        val session = database.workoutSessionDao().getById(sessionId)!!
        assertEquals(setId, session.activeDurationSetId)
        assertEquals(4_000L, session.durationStartsAt)
        assertEquals(64_000L, session.durationEndsAt)
        assertNull(session.restEndsAt)
        assertEquals(64_000L, durationTimerScheduler.scheduledEndsAt)
        assertTrue(restTimerScheduler.cancelled)
    }

    @Test
    fun durationPreparationSupportsOffThreeAndFiveSeconds() = runTest {
        listOf(0, 3, 5).forEach { preparationSeconds ->
            repository = createRepository(DurationPreparationProvider { preparationSeconds })
            val sessionId = repository.startWorkout(seedDurationWorkout(60))
            val setId = firstSessionSets(sessionId).first().id

            assertTrue(repository.startDurationSet(sessionId, setId, now = 1_000L))
            val session = database.workoutSessionDao().getById(sessionId)!!
            assertEquals(1_000L + preparationSeconds * 1_000L, session.durationStartsAt)
            assertEquals(61_000L + preparationSeconds * 1_000L, session.durationEndsAt)

            repository.discardActiveWorkout()
        }
    }

    @Test
    fun changingPreparationDoesNotShiftAnAlreadyRunningTimer() = runTest {
        var preparationSeconds = 3
        repository = createRepository(DurationPreparationProvider { preparationSeconds })
        val sessionId = repository.startWorkout(seedDurationWorkout(60))
        val setId = firstSessionSets(sessionId).first().id
        repository.startDurationSet(sessionId, setId, now = 1_000L)
        preparationSeconds = 5

        assertTrue(repository.startDurationSet(sessionId, setId, now = 2_000L))
        val session = database.workoutSessionDao().getById(sessionId)!!
        assertEquals(4_000L, session.durationStartsAt)
        assertEquals(64_000L, session.durationEndsAt)
    }

    @Test
    fun cancellingDuringPrepLeavesSetPendingAndClearsTimer() = runTest {
        val sessionId = repository.startWorkout(seedDurationWorkout(60))
        val setId = firstSessionSets(sessionId).first().id
        repository.startDurationSet(sessionId, setId, now = 1_000L)

        assertTrue(repository.cancelDurationSet(sessionId, setId, now = 3_999L))

        assertEquals(SessionSetStatus.PENDING, database.sessionSetDao().getById(setId)!!.status)
        assertNull(database.workoutSessionDao().getById(sessionId)!!.activeDurationSetId)
        assertTrue(durationTimerScheduler.cancelled)
    }

    @Test
    fun earlyStopFloorsElapsedSecondsAndUsesNormalRest() = runTest {
        val sessionId = repository.startWorkout(seedDurationWorkout(60))
        val setId = firstSessionSets(sessionId).first().id
        repository.startDurationSet(sessionId, setId, now = 1_000L)

        assertTrue(repository.stopDurationSet(sessionId, setId, now = 63_100L))

        val set = database.sessionSetDao().getById(setId)!!
        val session = database.workoutSessionDao().getById(sessionId)!!
        assertEquals(SessionSetStatus.COMPLETED, set.status)
        assertEquals(59, set.actualDurationSeconds)
        assertEquals(63_100L, set.completedAt)
        assertNull(session.activeDurationSetId)
        assertEquals(243_100L, session.restEndsAt)
    }

    @Test
    fun expiredDurationReconcilesExactlyOnceAtTarget() = runTest {
        val sessionId = repository.startWorkout(seedDurationWorkout(60))
        val setId = firstSessionSets(sessionId).first().id
        repository.startDurationSet(sessionId, setId, now = 1_000L)

        assertTrue(repository.reconcileDurationTimer(now = 64_000L))
        assertEquals(false, repository.reconcileDurationTimer(now = 65_000L))

        val set = database.sessionSetDao().getById(setId)!!
        assertEquals(SessionSetStatus.COMPLETED, set.status)
        assertEquals(60, set.actualDurationSeconds)
        assertEquals(64_000L, set.completedAt)
    }

    @Test
    fun durationCommandsRejectWrongSetAndDuplicateStopSafely() = runTest {
        val sessionId = repository.startWorkout(seedDurationWorkout(60))
        val sets = firstSessionSets(sessionId).sortedBy { it.setOrder }
        assertEquals(false, repository.startDurationSet(sessionId, sets[1].id, now = 1_000L))
        assertTrue(repository.startDurationSet(sessionId, sets[0].id, now = 1_000L))
        assertTrue(repository.startDurationSet(sessionId, sets[0].id, now = 2_000L))
        assertEquals(false, repository.stopDurationSet(sessionId, sets[1].id, now = 5_000L))
        assertTrue(repository.stopDurationSet(sessionId, sets[0].id, now = 5_000L))
        assertEquals(false, repository.stopDurationSet(sessionId, sets[0].id, now = 6_000L))
    }

    @Test
    fun runningDurationBlocksFinishAndDiscardClearsAlarm() = runTest {
        val sessionId = repository.startWorkout(seedDurationWorkout(60))
        val setId = firstSessionSets(sessionId).first().id
        repository.startDurationSet(sessionId, setId, now = 1_000L)

        runCatching { repository.finishActiveWorkout(allowPartial = true) }
            .onSuccess { fail("Finish should require stopping the duration set.") }
        repository.discardActiveWorkout()

        assertNull(database.workoutSessionDao().getActive())
        assertTrue(durationTimerScheduler.cancelled)
    }

    @Test
    fun durationTimerProjectsToWearAndReschedulesAfterRepositoryRecreation() = runTest {
        val sessionId = repository.startWorkout(seedDurationWorkout(60))
        val setId = firstSessionSets(sessionId).first().id
        repository.startDurationSet(sessionId, setId, now = 1_000L)
        val wear = WorkoutWearStateProjector.stateFor(database.workoutSessionDao().getActiveWithDetails(), now = 2_000L)
        assertEquals(4_000L, wear.durationStartsAt)
        assertEquals(64_000L, wear.durationEndsAt)

        val restartedDurationScheduler = FakeDurationTimerScheduler()
        val restartedRepository = WorkoutSessionRepository(
            database, database.workoutSessionDao(), database.sessionExerciseDao(), database.sessionSetDao(),
            database.programDao(), database.workoutTemplateDao(), database.workoutTemplateExerciseDao(),
            database.workoutTemplateSetTargetDao(), database.workoutTemplateWarmupSetDao(),
            database.progressionStateDao(), database.supersetGroupDao(), FakeRestTimerScheduler(),
            restartedDurationScheduler,
        )
        restartedRepository.reconcileDurationTimer(now = 2_000L)
        assertEquals(64_000L, restartedDurationScheduler.scheduledEndsAt)
    }

    @Test
    fun backupRoundTripPreservesRunningDurationTimerForRescheduling() = runTest {
        val now = System.currentTimeMillis()
        val sessionId = repository.startWorkout(seedDurationWorkout(60))
        val setId = firstSessionSets(sessionId).first().id
        repository.startDurationSet(sessionId, setId, now = now)
        val before = database.workoutSessionDao().getById(sessionId)!!
        val output = java.io.ByteArrayOutputStream()
        DataBackupRepository(database).exportBackup(output)

        repository.discardActiveWorkout()
        DataBackupRepository(database).restoreBackup(java.io.ByteArrayInputStream(output.toByteArray()))
        repository.reconcileDurationTimer(now = now + 1_000L)

        val restored = database.workoutSessionDao().getById(sessionId)!!
        assertEquals(before.activeDurationSetId, restored.activeDurationSetId)
        assertEquals(before.durationStartsAt, restored.durationStartsAt)
        assertEquals(before.durationEndsAt, restored.durationEndsAt)
        assertEquals(before.durationEndsAt, durationTimerScheduler.scheduledEndsAt)
    }

    @Test
    fun durationAutoCompletionUsesSupersetRoundRestRules() = runTest {
        val seed = seedTwoExerciseWorkout()
        val groupId = database.supersetGroupDao().insert(
            SupersetGroupEntity(workoutTemplateId = seed.templateId, restSeconds = 90),
        )
        listOf(seed.firstTemplateExerciseId, seed.secondTemplateExerciseId).forEach { id ->
            val exercise = database.workoutTemplateExerciseDao().getById(id)!!
            database.workoutTemplateExerciseDao().update(
                exercise.copy(
                    supersetGroupId = groupId,
                    trackingMode = TrackingMode.DURATION,
                    targetDurationSeconds = 60,
                ),
            )
        }
        val sessionId = repository.startWorkout(seed.templateId)
        val exercises = database.sessionExerciseDao().getForSession(sessionId)
        val firstSet = database.sessionSetDao().getForSessionExercise(exercises[0].id).first()
        val secondSet = database.sessionSetDao().getForSessionExercise(exercises[1].id).first()

        repository.startDurationSet(sessionId, firstSet.id, now = 1_000L)
        repository.reconcileDurationTimer(now = 64_000L)
        assertNull(database.workoutSessionDao().getById(sessionId)!!.restEndsAt)
        assertEquals(secondSet.id, WorkoutWearStateProjector.stateFor(database.workoutSessionDao().getActiveWithDetails()).currentSetId)

        repository.startDurationSet(sessionId, secondSet.id, now = 65_000L)
        repository.reconcileDurationTimer(now = 128_000L)
        assertEquals(218_000L, database.workoutSessionDao().getById(sessionId)!!.restEndsAt)
    }

    private suspend fun seedDurationWorkout(targetSeconds: Int): Long {
        val templateId = seedBenchWorkout()
        val template = database.workoutTemplateExerciseDao().getForWorkoutTemplate(templateId).single()
        database.workoutTemplateExerciseDao().update(
            template.copy(trackingMode = TrackingMode.DURATION, targetDurationSeconds = targetSeconds),
        )
        return templateId
    }

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

    private class FakeDurationTimerScheduler : DurationTimerScheduler {
        var scheduledEndsAt: Long? = null
            private set
        var cancelled = false
            private set

        override fun schedule(durationEndsAt: Long) {
            scheduledEndsAt = durationEndsAt
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
