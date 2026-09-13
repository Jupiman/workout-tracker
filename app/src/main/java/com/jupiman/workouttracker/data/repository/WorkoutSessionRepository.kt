package com.jupiman.workouttracker.data.repository

import androidx.room.withTransaction
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import com.jupiman.workouttracker.data.local.dao.ProgramDao
import com.jupiman.workouttracker.data.local.dao.ProgressionStateDao
import com.jupiman.workouttracker.data.local.dao.SessionExerciseDao
import com.jupiman.workouttracker.data.local.dao.SessionSetDao
import com.jupiman.workouttracker.data.local.dao.SupersetGroupDao
import com.jupiman.workouttracker.data.local.dao.WorkoutSessionDao
import com.jupiman.workouttracker.data.local.dao.WorkoutTemplateDao
import com.jupiman.workouttracker.data.local.dao.WorkoutTemplateExerciseDao
import com.jupiman.workouttracker.data.local.dao.WorkoutTemplateSetTargetDao
import com.jupiman.workouttracker.data.local.dao.WorkoutTemplateWarmupSetDao
import com.jupiman.workouttracker.data.local.entity.SessionExerciseEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateSetTargetEntity
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import com.jupiman.workouttracker.domain.progression.ProgressionConfig
import com.jupiman.workouttracker.domain.progression.ProgressionEngine
import com.jupiman.workouttracker.domain.progression.ProgressionSet
import com.jupiman.workouttracker.notification.RestTimerScheduler
import com.jupiman.workouttracker.notification.NoOpWorkoutNotificationUpdater
import com.jupiman.workouttracker.notification.WorkoutNotificationProjector
import com.jupiman.workouttracker.notification.WorkoutNotificationUpdater
import kotlin.math.max
import kotlin.math.roundToInt

class WorkoutSessionRepository(
    private val database: WorkoutTrackerDatabase,
    private val workoutSessionDao: WorkoutSessionDao,
    private val sessionExerciseDao: SessionExerciseDao,
    private val sessionSetDao: SessionSetDao,
    private val programDao: ProgramDao,
    private val workoutTemplateDao: WorkoutTemplateDao,
    private val workoutTemplateExerciseDao: WorkoutTemplateExerciseDao,
    private val workoutTemplateSetTargetDao: WorkoutTemplateSetTargetDao,
    private val workoutTemplateWarmupSetDao: WorkoutTemplateWarmupSetDao,
    private val progressionStateDao: ProgressionStateDao,
    private val supersetGroupDao: SupersetGroupDao,
    private val restTimerScheduler: RestTimerScheduler,
    private val workoutNotificationUpdater: WorkoutNotificationUpdater = NoOpWorkoutNotificationUpdater,
) {
    val activeSession = workoutSessionDao.observeActive()
    val activeSessionWithDetails = workoutSessionDao.observeActiveWithDetails()
    val latestFinishedSessionForActiveProgram = workoutSessionDao.observeLatestFinishedForActiveProgram()
    val history = workoutSessionDao.observeHistory()
    val historyWithDetails = workoutSessionDao.observeHistoryWithDetails()

    fun sessionExercises(sessionId: Long) = sessionExerciseDao.observeForSession(sessionId)

    fun sessionSets(sessionExerciseId: Long) = sessionSetDao.observeForSessionExercise(sessionExerciseId)

    suspend fun startWorkout(workoutTemplateId: Long): Long {
        val sessionId = database.withTransaction {
            val existingActiveSession = workoutSessionDao.getActive()
            require(existingActiveSession == null) { "Finish or discard the active workout first." }

            val template = workoutTemplateDao.getById(workoutTemplateId)
                ?: error("Workout template not found.")
            val program = programDao.getById(template.programId)
                ?: error("Program not found.")
            val templateExercises = workoutTemplateExerciseDao.getEditorItemsForWorkoutTemplate(workoutTemplateId)
            require(templateExercises.isNotEmpty()) { "Add at least one exercise before starting this workout." }

            val now = System.currentTimeMillis()
            val sessionId = workoutSessionDao.insert(
                WorkoutSessionEntity(
                    sourceWorkoutTemplateId = template.id,
                    sourceProgramId = program.id,
                    programNameSnapshot = program.name,
                    workoutNameSnapshot = template.name,
                    startedAt = now,
                ),
            )

            templateExercises.forEach { templateExercise ->
                val supersetRestSeconds = templateExercise.supersetGroupId?.let { supersetGroupId ->
                    supersetGroupDao.getById(supersetGroupId)?.restSeconds
                }
                val sessionExerciseId = sessionExerciseDao.insert(
                    SessionExerciseEntity(
                        sessionId = sessionId,
                        sourceWorkoutTemplateExerciseId = templateExercise.id,
                        exerciseNameSnapshot = templateExercise.exerciseName,
                        sortOrderSnapshot = templateExercise.sortOrder,
                        plannedSetCountSnapshot = templateExercise.plannedWorkingSets,
                        repMinSnapshot = templateExercise.repMin,
                        repMaxSnapshot = templateExercise.repMax,
                        targetRepsSnapshot = templateExercise.currentTargetReps,
                        prescribedWeightCentiKgSnapshot = templateExercise.currentWeightCentiKg,
                        incrementCentiKgSnapshot = templateExercise.incrementCentiKg,
                        restSecondsSnapshot = templateExercise.restSeconds,
                        supersetGroupSnapshot = templateExercise.supersetGroupId,
                        supersetRestSecondsSnapshot = supersetRestSeconds,
                    ),
                )

                val warmupSets = workoutTemplateWarmupSetDao
                    .getForTemplateExercise(templateExercise.id)
                val generatedWarmupSets = warmupSets.mapIndexed { index, warmupSet ->
                    SessionSetEntity(
                        sessionExerciseId = sessionExerciseId,
                        setOrder = index - warmupSets.size,
                        setType = SetType.WARMUP,
                        isPlanned = true,
                        countsForProgression = false,
                        prescribedWeightCentiKg = warmupWeightCentiKg(
                            workingWeightCentiKg = templateExercise.currentWeightCentiKg,
                            percent = warmupSet.percentOfWorkingWeight,
                        ),
                        prescribedReps = warmupSet.reps,
                    )
                }
                if (generatedWarmupSets.isNotEmpty()) {
                    sessionSetDao.insertAll(generatedWarmupSets)
                }

                val setTargetsByOrder = workoutTemplateSetTargetDao
                    .getForTemplateExercise(templateExercise.id)
                    .associateBy { it.setOrder }
                val plannedSets = (0 until templateExercise.plannedWorkingSets).map { setIndex ->
                    val setTarget = setTargetsByOrder[setIndex]
                    SessionSetEntity(
                        sessionExerciseId = sessionExerciseId,
                        setOrder = setIndex,
                        setType = SetType.WORKING,
                        isPlanned = true,
                        countsForProgression = setTarget?.countsForProgression ?: true,
                        prescribedWeightCentiKg = setTarget?.prescribedWeightCentiKg
                            ?: templateExercise.currentWeightCentiKg,
                        prescribedReps = setTarget?.prescribedReps
                            ?: templateExercise.currentTargetReps,
                    )
                }
                sessionSetDao.insertAll(plannedSets)
            }

            sessionId
        }
        workoutNotificationUpdater.refresh()
        return sessionId
    }

    suspend fun completeSet(
        setId: Long,
        actualWeightCentiKg: Int,
        actualReps: Int,
    ) {
        require(actualWeightCentiKg >= 0) { "Weight cannot be negative." }
        require(actualReps >= 0) { "Reps cannot be negative." }

        completeSetInternal(
            setId = setId,
            expectedSessionId = null,
            actualWeightCentiKg = actualWeightCentiKg,
            actualReps = actualReps,
            pendingOnly = false,
        )
        workoutNotificationUpdater.refresh()
    }

    suspend fun completeSetFromNotification(
        expectedSessionId: Long,
        setId: Long,
    ): Boolean {
        val completed = completeSetInternal(
            setId = setId,
            expectedSessionId = expectedSessionId,
            actualWeightCentiKg = null,
            actualReps = null,
            pendingOnly = true,
            requireCurrentActionable = false,
        )
        if (completed) workoutNotificationUpdater.refresh()
        return completed
    }

    suspend fun completeSetFromWearCommand(
        expectedSessionId: Long,
        setId: Long,
    ): Boolean {
        val completed = completeSetInternal(
            setId = setId,
            expectedSessionId = expectedSessionId,
            actualWeightCentiKg = null,
            actualReps = null,
            pendingOnly = true,
            requireCurrentActionable = true,
        )
        if (completed) workoutNotificationUpdater.refresh()
        return completed
    }

    private suspend fun completeSetInternal(
        setId: Long,
        expectedSessionId: Long?,
        actualWeightCentiKg: Int?,
        actualReps: Int?,
        pendingOnly: Boolean,
        requireCurrentActionable: Boolean = false,
    ): Boolean {
        var restEndsAtToSchedule: Long? = null
        var shouldCancelRest = false
        var completed = false
        database.withTransaction {
            val set = sessionSetDao.getById(setId) ?: error("Set not found.")
            val sessionExercise = sessionExerciseDao.getById(set.sessionExerciseId)
                ?: error("Session exercise not found.")
            val session = workoutSessionDao.getById(sessionExercise.sessionId)
                ?: error("Workout session not found.")
            if (expectedSessionId != null && session.id != expectedSessionId) return@withTransaction
            if (pendingOnly && set.status != SessionSetStatus.PENDING) return@withTransaction
            if (requireCurrentActionable) {
                val activeWorkout = workoutSessionDao.getActiveWithDetails() ?: return@withTransaction
                val currentSetId = WorkoutNotificationProjector.nextActionableSet(activeWorkout)?.setId
                if (activeWorkout.session.id != session.id || currentSetId != setId) return@withTransaction
            }
            require(session.status == WorkoutSessionStatus.ACTIVE) { "Only active workouts can be edited." }
            val now = System.currentTimeMillis()
            sessionSetDao.update(
                set.copy(
                    actualWeightCentiKg = actualWeightCentiKg
                        ?: set.prescribedWeightCentiKg
                        ?: sessionExercise.prescribedWeightCentiKgSnapshot,
                    actualReps = actualReps
                        ?: set.prescribedReps
                        ?: sessionExercise.targetRepsSnapshot,
                    status = SessionSetStatus.COMPLETED,
                    completedAt = now,
                ),
            )
            completed = true

            val restSeconds = restSecondsAfterCompletedWorkingSet(
                completedSet = set,
                sessionExercise = sessionExercise,
                sessionExercises = sessionExerciseDao.getForSession(session.id),
            )
            if (restSeconds != null && restSeconds > 0) {
                val restEndsAt = now + restSeconds * 1_000L
                workoutSessionDao.update(session.copy(restEndsAt = restEndsAt))
                restEndsAtToSchedule = restEndsAt
            } else if (restSeconds != null) {
                workoutSessionDao.update(session.copy(restEndsAt = null))
                shouldCancelRest = true
            }
        }
        restEndsAtToSchedule?.let(restTimerScheduler::schedule)
        if (shouldCancelRest) restTimerScheduler.cancel()
        return completed
    }

    suspend fun addSessionSet(
        sessionExerciseId: Long,
        setType: SetType,
    ): Long {
        val setId = database.withTransaction {
            require(setType != SetType.WORKING && setType != SetType.WARMUP) {
                "Only session-only sets can be added."
            }
            val sessionExercise = sessionExerciseDao.getById(sessionExerciseId)
                ?: error("Session exercise not found.")
            val session = workoutSessionDao.getById(sessionExercise.sessionId)
                ?: error("Workout session not found.")
            require(session.status == WorkoutSessionStatus.ACTIVE) { "Only active workouts can be edited." }

            val existingSets = sessionSetDao.getForSessionExercise(sessionExerciseId)
            val previousSet = existingSets.maxByOrNull { it.setOrder }
            val defaultWeight = when (setType) {
                SetType.EXTRA,
                SetType.AMRAP -> sessionExercise.prescribedWeightCentiKgSnapshot
                SetType.DROP -> previousSet?.actualWeightCentiKg
                    ?: previousSet?.prescribedWeightCentiKg
                    ?: sessionExercise.prescribedWeightCentiKgSnapshot
                SetType.WARMUP,
                SetType.WORKING -> error("Working sets are generated from templates.")
            }
            val defaultReps = when (setType) {
                SetType.EXTRA -> sessionExercise.targetRepsSnapshot
                SetType.AMRAP,
                SetType.DROP -> null
                SetType.WARMUP,
                SetType.WORKING -> error("Working sets are generated from templates.")
            }

            sessionSetDao.insert(
                SessionSetEntity(
                    sessionExerciseId = sessionExerciseId,
                    setOrder = sessionSetDao.countForSessionExercise(sessionExerciseId),
                    setType = setType,
                    isPlanned = false,
                    countsForProgression = false,
                    prescribedWeightCentiKg = defaultWeight,
                    prescribedReps = defaultReps,
                ),
            )
        }
        workoutNotificationUpdater.refresh()
        return setId
    }

    suspend fun uncompleteSet(setId: Long) {
        database.withTransaction {
            val set = sessionSetDao.getById(setId) ?: error("Set not found.")
            sessionSetDao.update(
                set.copy(
                    actualWeightCentiKg = null,
                    actualReps = null,
                    status = SessionSetStatus.PENDING,
                    completedAt = null,
                ),
            )
        }
        workoutNotificationUpdater.refresh()
    }

    suspend fun skipSet(setId: Long) {
        database.withTransaction {
            val set = sessionSetDao.getById(setId) ?: error("Set not found.")
            sessionSetDao.update(
                set.copy(
                    actualWeightCentiKg = null,
                    actualReps = null,
                    status = SessionSetStatus.SKIPPED,
                    completedAt = System.currentTimeMillis(),
                ),
            )
        }
        workoutNotificationUpdater.refresh()
    }

    suspend fun discardActiveWorkout() {
        database.withTransaction {
            workoutSessionDao.getActive()?.let { activeSession ->
                workoutSessionDao.deleteActiveById(activeSession.id)
            }
        }
        restTimerScheduler.cancel()
        workoutNotificationUpdater.cancel()
    }

    suspend fun finishActiveWorkout(
        allowPartial: Boolean,
        progressionChoices: Map<Long, ProgressionFinishChoice> = emptyMap(),
    ) {
        database.withTransaction {
            val activeSession = workoutSessionDao.getActive()
                ?: error("No active workout to finish.")
            require(!activeSession.progressionApplied) { "Progression has already been applied." }

            val sessionExercises = sessionExerciseDao.getForSession(activeSession.id)
            val setsByExerciseId = sessionExercises.associate { sessionExercise ->
                sessionExercise.id to sessionSetDao.getForSessionExercise(sessionExercise.id)
            }
            val allPlannedSetsCompleted = setsByExerciseId.values
                .flatten()
                .filter { it.isPlanned && it.setType != SetType.WARMUP }
                .all { it.status == SessionSetStatus.COMPLETED }

            if (!allPlannedSetsCompleted && !allowPartial) {
                throw IllegalStateException("Some planned sets are incomplete.")
            }

            val completedAt = System.currentTimeMillis()
            val finalSetsByExerciseId = setsByExerciseId.mapValues { (_, exerciseSets) ->
                exerciseSets.map { set ->
                    if (set.status == SessionSetStatus.PENDING &&
                        (set.setType == SetType.WARMUP || (!allPlannedSetsCompleted && allowPartial))
                    ) {
                        val skippedSet = set.copy(
                            status = SessionSetStatus.SKIPPED,
                            actualWeightCentiKg = null,
                            actualReps = null,
                            completedAt = completedAt,
                        )
                        sessionSetDao.update(skippedSet)
                        skippedSet
                    } else {
                        set
                    }
                }
            }

            sessionExercises.forEach { sessionExercise ->
                val sourceTemplateExerciseId = sessionExercise.sourceWorkoutTemplateExerciseId
                    ?: return@forEach
                val progressionState = progressionStateDao.getForTemplateExercise(sourceTemplateExerciseId)
                    ?: return@forEach
                val exerciseSets = finalSetsByExerciseId.getValue(sessionExercise.id)
                val changedProgressionSets = exerciseSets.changedProgressionSets()
                if (changedProgressionSets.isNotEmpty()) {
                    changedProgressionSets.forEach { changedSet ->
                        when (progressionChoices[changedSet.id] ?: ProgressionFinishChoice.NO_CHANGE) {
                            ProgressionFinishChoice.NO_CHANGE -> Unit
                            ProgressionFinishChoice.CHANGE_THIS_SET -> {
                                upsertTemplateSetTarget(
                                    workoutTemplateExerciseId = sourceTemplateExerciseId,
                                    set = changedSet,
                                    fallbackWeightCentiKg = sessionExercise.prescribedWeightCentiKgSnapshot,
                                    fallbackReps = sessionExercise.targetRepsSnapshot,
                                )
                            }
                            ProgressionFinishChoice.SET_TARGET_FOR_EXERCISE -> {
                                val target = changedSet.loggedTarget(
                                    fallbackWeightCentiKg = sessionExercise.prescribedWeightCentiKgSnapshot,
                                    fallbackReps = sessionExercise.targetRepsSnapshot,
                                )
                                workoutTemplateSetTargetDao.deleteForTemplateExercise(sourceTemplateExerciseId)
                                progressionStateDao.update(
                                    progressionState.copy(
                                        currentWeightCentiKg = target.weightCentiKg,
                                        currentTargetReps = target.targetReps.coerceIn(
                                            sessionExercise.repMinSnapshot,
                                            sessionExercise.repMaxSnapshot,
                                        ),
                                        updatedAt = System.currentTimeMillis(),
                                    ),
                                )
                            }
                        }
                    }
                    return@forEach
                }

                val result = ProgressionEngine.evaluate(
                    config = ProgressionConfig(
                        currentWeightCentiKg = sessionExercise.prescribedWeightCentiKgSnapshot,
                        currentTargetReps = sessionExercise.targetRepsSnapshot,
                        repMin = sessionExercise.repMinSnapshot,
                        repMax = sessionExercise.repMaxSnapshot,
                        incrementCentiKg = sessionExercise.incrementCentiKgSnapshot,
                    ),
                    sets = exerciseSets.map { set ->
                        ProgressionSet(
                            countsForProgression = set.countsForProgression,
                            prescribedWeightCentiKg = set.prescribedWeightCentiKg,
                            prescribedReps = set.prescribedReps,
                            actualWeightCentiKg = set.actualWeightCentiKg,
                            actualReps = set.actualReps,
                            status = set.status,
                        )
                    },
                )

                if (result.progressed) {
                    progressionStateDao.update(
                        progressionState.copy(
                            currentWeightCentiKg = result.nextWeightCentiKg,
                            currentTargetReps = result.nextTargetReps,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            }

            workoutSessionDao.update(
                activeSession.copy(
                    completedAt = completedAt,
                    status = if (allPlannedSetsCompleted) {
                        WorkoutSessionStatus.COMPLETED
                    } else {
                        WorkoutSessionStatus.PARTIAL
                    },
                    restEndsAt = null,
                    progressionApplied = true,
                ),
            )
        }
        restTimerScheduler.cancel()
        workoutNotificationUpdater.cancel()
    }

    suspend fun addRestTime(seconds: Int) {
        require(seconds > 0) { "Rest time adjustment must be positive." }
        val restEndsAt = database.withTransaction {
            val activeSession = workoutSessionDao.getActive()
                ?: error("No active workout.")
            val now = System.currentTimeMillis()
            val updatedRestEndsAt = max(activeSession.restEndsAt ?: now, now) + seconds * 1_000L
            workoutSessionDao.update(activeSession.copy(restEndsAt = updatedRestEndsAt))
            updatedRestEndsAt
        }
        restTimerScheduler.schedule(restEndsAt)
        workoutNotificationUpdater.refresh()
    }

    suspend fun skipRest() {
        database.withTransaction {
            workoutSessionDao.getActive()?.let { activeSession ->
                workoutSessionDao.update(activeSession.copy(restEndsAt = null))
            }
        }
        restTimerScheduler.cancel()
        workoutNotificationUpdater.refresh()
    }

    suspend fun syncRestTimerAlarm() {
        val restEndsAt = database.withTransaction {
            workoutSessionDao.getActive()?.restEndsAt
        }
        if (restEndsAt != null && restEndsAt > System.currentTimeMillis()) {
            restTimerScheduler.schedule(restEndsAt)
        } else {
            restTimerScheduler.cancel()
        }
        workoutNotificationUpdater.refresh()
    }

    private fun restSecondsAfterCompletedWorkingSet(
        completedSet: SessionSetEntity,
        sessionExercise: SessionExerciseEntity,
        sessionExercises: List<SessionExerciseEntity>,
    ): Int? {
        if (completedSet.setType != SetType.WORKING) return null

        val supersetGroupId = sessionExercise.supersetGroupSnapshot
        if (supersetGroupId == null) return sessionExercise.restSecondsSnapshot

        val groupExercises = sessionExercises
            .filter { it.supersetGroupSnapshot == supersetGroupId }
            .sortedBy { it.sortOrderSnapshot }
        if (groupExercises.isEmpty()) return sessionExercise.restSecondsSnapshot

        val isLastExerciseInSuperset = groupExercises.last().id == sessionExercise.id
        return if (isLastExerciseInSuperset) {
            sessionExercise.supersetRestSecondsSnapshot ?: sessionExercise.restSecondsSnapshot
        } else {
            0
        }
    }

    private fun warmupWeightCentiKg(
        workingWeightCentiKg: Int,
        percent: Int,
    ): Int {
        if (workingWeightCentiKg <= 0 || percent <= 0) return 0
        val rawWarmupCentiKg = workingWeightCentiKg * percent / 100.0
        val fiveKgCentiKg = 500
        val rounded = (rawWarmupCentiKg / fiveKgCentiKg).roundToInt() * fiveKgCentiKg
        return rounded.coerceIn(0, workingWeightCentiKg)
    }

    private fun List<SessionSetEntity>.changedProgressionSets(): List<SessionSetEntity> =
        filter {
            it.countsForProgression &&
                it.status == SessionSetStatus.COMPLETED &&
                (it.actualWeightCentiKg != it.prescribedWeightCentiKg ||
                    it.actualReps != it.prescribedReps)
        }.sortedBy { it.setOrder }

    private suspend fun upsertTemplateSetTarget(
        workoutTemplateExerciseId: Long,
        set: SessionSetEntity,
        fallbackWeightCentiKg: Int,
        fallbackReps: Int,
    ) {
        val existingTarget = workoutTemplateSetTargetDao.getForTemplateExerciseSetOrder(
            workoutTemplateExerciseId = workoutTemplateExerciseId,
            setOrder = set.setOrder,
        )
        val target = set.loggedTarget(
            fallbackWeightCentiKg = fallbackWeightCentiKg,
            fallbackReps = fallbackReps,
        )
        val entity = WorkoutTemplateSetTargetEntity(
            id = existingTarget?.id ?: 0,
            workoutTemplateExerciseId = workoutTemplateExerciseId,
            setOrder = set.setOrder,
            prescribedWeightCentiKg = target.weightCentiKg,
            prescribedReps = target.targetReps,
            countsForProgression = set.countsForProgression,
        )
        if (existingTarget == null) {
            workoutTemplateSetTargetDao.insert(entity)
        } else {
            workoutTemplateSetTargetDao.update(entity)
        }
    }

    private fun SessionSetEntity.loggedTarget(
        fallbackWeightCentiKg: Int,
        fallbackReps: Int,
    ): LoggedProgressionTarget =
        LoggedProgressionTarget(
            weightCentiKg = actualWeightCentiKg ?: prescribedWeightCentiKg ?: fallbackWeightCentiKg,
            targetReps = actualReps ?: prescribedReps ?: fallbackReps,
        )

    private data class LoggedProgressionTarget(
        val weightCentiKg: Int,
        val targetReps: Int,
    )
}
