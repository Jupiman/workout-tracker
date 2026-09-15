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
import com.jupiman.workouttracker.data.local.entity.TrackingMode
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateSetTargetEntity
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import com.jupiman.workouttracker.domain.progression.ProgressionConfig
import com.jupiman.workouttracker.domain.progression.ProgressionEngine
import com.jupiman.workouttracker.domain.progression.ProgressionSet
import com.jupiman.workouttracker.notification.RestTimerScheduler
import com.jupiman.workouttracker.notification.DurationTimerScheduler
import com.jupiman.workouttracker.notification.NoOpDurationTimerScheduler
import com.jupiman.workouttracker.notification.NoOpWorkoutNotificationUpdater
import com.jupiman.workouttracker.notification.WorkoutNotificationProjector
import com.jupiman.workouttracker.notification.WorkoutNotificationUpdater
import com.jupiman.workouttracker.preferences.DefaultDurationPreparationProvider
import com.jupiman.workouttracker.preferences.DurationPreparationProvider
import com.jupiman.workouttracker.healthconnect.FinalizedWorkoutSync
import com.jupiman.workouttracker.healthconnect.NoOpFinalizedWorkoutSync
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    private val durationTimerScheduler: DurationTimerScheduler = NoOpDurationTimerScheduler,
    private val workoutNotificationUpdater: WorkoutNotificationUpdater = NoOpWorkoutNotificationUpdater,
    private val durationPreparationProvider: DurationPreparationProvider = DefaultDurationPreparationProvider,
    private val finalizedWorkoutSync: FinalizedWorkoutSync = NoOpFinalizedWorkoutSync,
) {
    // Only the short-lived affordance is transient; the set and timer live in Room.
    private var latestUndo: SetCompletionUndo? = null
    private var undoRestDeadline: Long? = null
    private val _completionSummary = MutableStateFlow<WorkoutCompletionSummary?>(null)
    val completionSummary = _completionSummary.asStateFlow()

    fun dismissCompletionSummary(sessionId: Long) {
        if (_completionSummary.value?.sessionId == sessionId) _completionSummary.value = null
    }
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
                        setupNoteSnapshot = templateExercise.setupNote,
                        trackingModeSnapshot = templateExercise.trackingMode,
                        targetDurationSecondsSnapshot = templateExercise.targetDurationSeconds,
                        durationIncrementSecondsSnapshot = templateExercise.durationIncrementSeconds,
                        supersetGroupSnapshot = templateExercise.supersetGroupId,
                        supersetRestSecondsSnapshot = supersetRestSeconds,
                    ),
                )

                val setTargetsByOrder = workoutTemplateSetTargetDao
                    .getForTemplateExercise(templateExercise.id)
                    .associateBy { it.setOrder }
                val warmupReferenceWeight = warmupReferenceWeightCentiKg(
                    defaultWorkingWeightCentiKg = templateExercise.currentWeightCentiKg,
                    plannedWorkingSets = templateExercise.plannedWorkingSets,
                    setTargets = setTargetsByOrder.values.toList(),
                )
                val warmupSets = if (templateExercise.trackingMode == TrackingMode.WEIGHT_REPS) workoutTemplateWarmupSetDao
                    .getForTemplateExercise(templateExercise.id) else emptyList()
                val generatedWarmupSets = warmupSets.mapIndexed { index, warmupSet ->
                    SessionSetEntity(
                        sessionExerciseId = sessionExerciseId,
                        setOrder = index - warmupSets.size,
                        setType = SetType.WARMUP,
                        isPlanned = true,
                        countsForProgression = false,
                        prescribedWeightCentiKg = warmupPrescribedWeightCentiKg(
                            warmup = WarmupSetConfiguration(
                                reps = warmupSet.reps,
                                loadType = warmupSet.loadType,
                                percentOfWorkingWeight = warmupSet.percentOfWorkingWeight,
                                fixedWeightCentiKg = warmupSet.fixedWeightCentiKg,
                            ),
                            referenceWeightCentiKg = warmupReferenceWeight,
                            roundingCentiKg = templateExercise.warmupRoundingCentiKg,
                        ),
                        prescribedReps = warmupSet.reps,
                    )
                }
                if (generatedWarmupSets.isNotEmpty()) {
                    sessionSetDao.insertAll(generatedWarmupSets)
                }

                val plannedSets = (0 until templateExercise.plannedWorkingSets).map { setIndex ->
                    val setTarget = setTargetsByOrder[setIndex]
                    SessionSetEntity(
                        sessionExerciseId = sessionExerciseId,
                        setOrder = setIndex,
                        setType = SetType.WORKING,
                        isPlanned = true,
                        countsForProgression = templateExercise.trackingMode != TrackingMode.DURATION && (setTarget?.countsForProgression ?: true),
                        prescribedWeightCentiKg = if (templateExercise.trackingMode == TrackingMode.WEIGHT_REPS) setTarget?.prescribedWeightCentiKg
                            ?: templateExercise.currentWeightCentiKg else null,
                        prescribedReps = if (templateExercise.trackingMode != TrackingMode.DURATION) setTarget?.prescribedReps
                            ?: templateExercise.currentTargetReps else null,
                        prescribedDurationSeconds = if (templateExercise.trackingMode == TrackingMode.DURATION) templateExercise.targetDurationSeconds else null,
                    )
                }
                sessionSetDao.insertAll(plannedSets)
            }

            sessionId
        }
        _completionSummary.value = null
        workoutNotificationUpdater.refresh()
        return sessionId
    }

    suspend fun completeSet(
        setId: Long,
        actualWeightCentiKg: Int,
        actualReps: Int,
        actualDurationSeconds: Int? = null,
    ): SetCompletionUndo? {
        require(actualWeightCentiKg >= 0) { "Weight cannot be negative." }
        require(actualReps >= 0) { "Reps cannot be negative." }

        var undo: SetCompletionUndo? = null
        completeSetInternal(
            setId = setId,
            expectedSessionId = null,
            actualWeightCentiKg = actualWeightCentiKg,
            actualReps = actualReps,
            pendingOnly = false,
            onUndoAvailable = { undo = it },
            actualDurationSeconds = actualDurationSeconds,
        )
        workoutNotificationUpdater.refresh()
        return undo
    }

    suspend fun startDurationSet(
        expectedSessionId: Long,
        setId: Long,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val prepMillis = durationPreparationProvider.durationPrepSeconds() * 1_000L
        var endToSchedule: Long? = null
        val started = database.withTransaction {
            val activeWorkout = workoutSessionDao.getActiveWithDetails() ?: return@withTransaction false
            val session = activeWorkout.session
            if (session.id != expectedSessionId) return@withTransaction false
            if (session.activeDurationSetId == setId && session.durationStartsAt != null && session.durationEndsAt != null) {
                endToSchedule = session.durationEndsAt
                return@withTransaction true
            }
            if (session.activeDurationSetId != null) return@withTransaction false
            val currentSetId = WorkoutNotificationProjector.nextActionableSet(activeWorkout)?.setId
            if (currentSetId != setId) return@withTransaction false
            val set = sessionSetDao.getById(setId) ?: return@withTransaction false
            val exercise = sessionExerciseDao.getById(set.sessionExerciseId) ?: return@withTransaction false
            if (set.status != SessionSetStatus.PENDING || exercise.trackingModeSnapshot != TrackingMode.DURATION) {
                return@withTransaction false
            }
            val target = set.prescribedDurationSeconds ?: exercise.targetDurationSecondsSnapshot
            if (target == null || target <= 0) return@withTransaction false
            val startsAt = now + prepMillis
            val endsAt = startsAt + target * 1_000L
            workoutSessionDao.update(
                session.copy(
                    restEndsAt = null,
                    activeDurationSetId = setId,
                    durationStartsAt = startsAt,
                    durationEndsAt = endsAt,
                ),
            )
            endToSchedule = endsAt
            true
        }
        if (started) {
            restTimerScheduler.cancel()
            endToSchedule?.let(durationTimerScheduler::schedule)
            workoutNotificationUpdater.refresh()
        }
        return started
    }

    suspend fun cancelDurationSet(
        expectedSessionId: Long,
        setId: Long,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val cancelled = database.withTransaction {
            val session = workoutSessionDao.getActive() ?: return@withTransaction false
            if (session.id != expectedSessionId || session.activeDurationSetId != setId) return@withTransaction false
            val startsAt = session.durationStartsAt ?: return@withTransaction false
            if (now >= startsAt) return@withTransaction false
            workoutSessionDao.update(session.withoutDurationTimer())
            true
        }
        if (cancelled) {
            durationTimerScheduler.cancel()
            workoutNotificationUpdater.refresh()
        }
        return cancelled
    }

    suspend fun stopDurationSet(
        expectedSessionId: Long,
        setId: Long,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        val session = workoutSessionDao.getActive() ?: return false
        if (session.id != expectedSessionId || session.activeDurationSetId != setId) return false
        val startsAt = session.durationStartsAt ?: return false
        val endsAt = session.durationEndsAt ?: return false
        if (now < startsAt) return false
        val set = sessionSetDao.getById(setId) ?: return false
        val target = set.prescribedDurationSeconds
            ?: sessionExerciseDao.getById(set.sessionExerciseId)?.targetDurationSecondsSnapshot
            ?: return false
        val elapsed = ((minOf(now, endsAt) - startsAt) / 1_000L).toInt().coerceIn(0, target)
        return completeSetInternal(
            setId = setId,
            expectedSessionId = expectedSessionId,
            actualWeightCentiKg = null,
            actualReps = null,
            actualDurationSeconds = elapsed,
            pendingOnly = true,
            requireCurrentActionable = true,
            expectedDurationTimerSetId = setId,
            completionTimeMillis = now,
        ).also { completed ->
            if (completed) {
                durationTimerScheduler.cancel()
                workoutNotificationUpdater.refresh()
            }
        }
    }

    suspend fun reconcileDurationTimer(now: Long = System.currentTimeMillis()): Boolean {
        val session = workoutSessionDao.getActive()
        val setId = session?.activeDurationSetId
        val endsAt = session?.durationEndsAt
        if (session == null || setId == null || session.durationStartsAt == null || endsAt == null) {
            durationTimerScheduler.cancel()
            return false
        }
        if (endsAt > now) {
            durationTimerScheduler.schedule(endsAt)
            return false
        }
        val set = sessionSetDao.getById(setId)
        val target = set?.prescribedDurationSeconds
            ?: set?.let { sessionExerciseDao.getById(it.sessionExerciseId)?.targetDurationSecondsSnapshot }
        if (set == null || target == null || target <= 0) {
            database.withTransaction {
                workoutSessionDao.getActive()?.takeIf { it.id == session.id && it.activeDurationSetId == setId }
                    ?.let { workoutSessionDao.update(it.withoutDurationTimer()) }
            }
            durationTimerScheduler.cancel()
            workoutNotificationUpdater.refresh()
            return false
        }
        val completed = completeSetInternal(
            setId = setId,
            expectedSessionId = session.id,
            actualWeightCentiKg = null,
            actualReps = null,
            actualDurationSeconds = target,
            pendingOnly = true,
            requireCurrentActionable = true,
            expectedDurationTimerSetId = setId,
            completionTimeMillis = endsAt,
        )
        if (!completed) {
            database.withTransaction {
                workoutSessionDao.getActive()?.takeIf { it.id == session.id && it.activeDurationSetId == setId }
                    ?.let { workoutSessionDao.update(it.withoutDurationTimer()) }
            }
        }
        durationTimerScheduler.cancel()
        workoutNotificationUpdater.refresh()
        return completed
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
            actualDurationSeconds = null,
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
            actualDurationSeconds = null,
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
        onUndoAvailable: (SetCompletionUndo?) -> Unit = {},
        actualDurationSeconds: Int? = null,
        expectedDurationTimerSetId: Long? = null,
        completionTimeMillis: Long = System.currentTimeMillis(),
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
            if (expectedDurationTimerSetId != null) {
                if (session.activeDurationSetId != expectedDurationTimerSetId) return@withTransaction
            } else {
                require(session.activeDurationSetId == null) { "Stop or cancel the running duration set first." }
            }
            latestUndo = null
            undoRestDeadline = null
            val now = completionTimeMillis
            val mode = sessionExercise.trackingModeSnapshot
            val duration = if (mode == TrackingMode.DURATION) actualDurationSeconds
                ?: set.prescribedDurationSeconds ?: sessionExercise.targetDurationSecondsSnapshot else null
            require(mode != TrackingMode.DURATION || (duration != null && duration >= 0)) { "Enter a valid duration in seconds." }
            val completedSet = set.copy(
                    actualWeightCentiKg = if (mode == TrackingMode.WEIGHT_REPS) actualWeightCentiKg
                        ?: set.prescribedWeightCentiKg
                        ?: sessionExercise.prescribedWeightCentiKgSnapshot else null,
                    actualReps = if (mode != TrackingMode.DURATION) actualReps
                        ?: set.prescribedReps
                        ?: sessionExercise.targetRepsSnapshot else null,
                    actualDurationSeconds = duration,
                    status = SessionSetStatus.COMPLETED,
                    completedAt = now,
                )
            sessionSetDao.update(completedSet)
            completed = true

            val restSeconds = restSecondsAfterCompletedWorkingSet(
                completedSet = set,
                sessionExercise = sessionExercise,
                sessionExercises = sessionExerciseDao.getForSession(session.id),
            )
            var updatedSession = if (expectedDurationTimerSetId != null) session.withoutDurationTimer() else session
            if (restSeconds != null && restSeconds > 0) {
                val restEndsAt = now + restSeconds * 1_000L
                updatedSession = updatedSession.copy(restEndsAt = restEndsAt)
                restEndsAtToSchedule = restEndsAt
            } else if (restSeconds != null) {
                updatedSession = updatedSession.copy(restEndsAt = null)
                shouldCancelRest = true
            }
            if (updatedSession != session) workoutSessionDao.update(updatedSession)
            if (set.status == SessionSetStatus.PENDING && !pendingOnly) {
                latestUndo = SetCompletionUndo(session.id, set, completedSet)
                undoRestDeadline = restEndsAtToSchedule
            }
            onUndoAvailable(latestUndo)
        }
        restEndsAtToSchedule?.let(restTimerScheduler::schedule)
        if (shouldCancelRest) restTimerScheduler.cancel()
        return completed
    }

    suspend fun undoCompletion(undo: SetCompletionUndo): Boolean {
        var cancelRest = false
        val restored = database.withTransaction {
            if (latestUndo !== undo) return@withTransaction false
            val session = workoutSessionDao.getActive() ?: return@withTransaction false
            if (session.id != undo.sessionId || sessionSetDao.getById(undo.completed.id) != undo.completed) {
                return@withTransaction false
            }
            sessionSetDao.update(undo.previous.copy(
                actualWeightCentiKg = undo.completed.actualWeightCentiKg,
                actualReps = undo.completed.actualReps,
                actualDurationSeconds = undo.completed.actualDurationSeconds,
            ))
            if (undoRestDeadline != null && session.restEndsAt == undoRestDeadline) {
                workoutSessionDao.update(session.copy(restEndsAt = null))
                cancelRest = true
            }
            latestUndo = null
            undoRestDeadline = null
            true
        }
        if (cancelRest) restTimerScheduler.cancel()
        if (restored) workoutNotificationUpdater.refresh()
        return restored
    }

    suspend fun skipExercise(sessionExerciseId: Long) {
        database.withTransaction {
            requireActiveExercise(sessionExerciseId)
            latestUndo = null
            sessionSetDao.getForSessionExercise(sessionExerciseId)
                .filter { it.status == SessionSetStatus.PENDING }
                .forEach { set ->
                    sessionSetDao.update(set.copy(
                        status = SessionSetStatus.SKIPPED,
                        actualWeightCentiKg = null,
                        actualReps = null,
                        actualDurationSeconds = null,
                        completedAt = System.currentTimeMillis(),
                    ))
                }
        }
        workoutNotificationUpdater.refresh()
    }

    suspend fun doExerciseLater(sessionExerciseId: Long) {
        database.withTransaction {
            val exercise = requireActiveExercise(sessionExerciseId)
            val exercises = sessionExerciseDao.getForSession(exercise.sessionId)
            val group = exercises.filter {
                it.id == exercise.id || (exercise.supersetGroupSnapshot != null &&
                    it.supersetGroupSnapshot == exercise.supersetGroupSnapshot)
            }
            require(group.any { member ->
                sessionSetDao.getForSessionExercise(member.id).any { it.status == SessionSetStatus.PENDING }
            }) { "No remaining sets to postpone." }
            val reordered = exercises.filterNot { it in group } + group
            reordered.forEachIndexed { index, member ->
                sessionExerciseDao.update(member.copy(sortOrderSnapshot = index))
            }
        }
        workoutNotificationUpdater.refresh()
    }

    suspend fun addExerciseForToday(
        sessionId: Long,
        exerciseId: Long,
        sets: Int,
        reps: Int,
        weightCentiKg: Int,
        restSeconds: Int,
        trackingMode: TrackingMode = TrackingMode.WEIGHT_REPS,
        durationSeconds: Int? = null,
    ): Long {
        require(sets >= 1) { "Sets must be at least 1." }
        require(trackingMode == TrackingMode.DURATION || reps >= 1) { "Reps must be at least 1." }
        require(trackingMode != TrackingMode.DURATION || (durationSeconds != null && durationSeconds > 0)) { "Duration must be at least 1 second." }
        require(weightCentiKg >= 0) { "Weight cannot be negative." }
        require(restSeconds >= 0) { "Rest time cannot be negative." }
        val id = database.withTransaction {
            val session = workoutSessionDao.getActive()
            require(session?.id == sessionId) { "Only active workouts can be edited." }
            requireNoDurationTimer(session)
            val exercise = database.exerciseDao().getById(exerciseId) ?: error("Exercise not found.")
            require(!exercise.archived) { "Choose an active exercise." }
            val order = (sessionExerciseDao.getForSession(sessionId).maxOfOrNull { it.sortOrderSnapshot } ?: -1) + 1
            val exerciseSnapshotId = sessionExerciseDao.insert(SessionExerciseEntity(
                sessionId = sessionId,
                sourceWorkoutTemplateExerciseId = null,
                exerciseNameSnapshot = exercise.name,
                trackingModeSnapshot = trackingMode,
                targetDurationSecondsSnapshot = if (trackingMode == TrackingMode.DURATION) durationSeconds else null,
                sortOrderSnapshot = order,
                plannedSetCountSnapshot = sets,
                repMinSnapshot = reps,
                repMaxSnapshot = reps,
                targetRepsSnapshot = reps,
                prescribedWeightCentiKgSnapshot = if (trackingMode == TrackingMode.WEIGHT_REPS) weightCentiKg else 0,
                incrementCentiKgSnapshot = 250,
                restSecondsSnapshot = restSeconds,
                setupNoteSnapshot = "",
                supersetGroupSnapshot = null,
                supersetRestSecondsSnapshot = null,
            ))
            sessionSetDao.insertAll((0 until sets).map { order ->
                SessionSetEntity(
                    sessionExerciseId = exerciseSnapshotId,
                    setOrder = order,
                    setType = SetType.WORKING,
                    isPlanned = true,
                    countsForProgression = false,
                    prescribedWeightCentiKg = if (trackingMode == TrackingMode.WEIGHT_REPS) weightCentiKg else null,
                    prescribedReps = if (trackingMode == TrackingMode.DURATION) null else reps,
                    prescribedDurationSeconds = if (trackingMode == TrackingMode.DURATION) durationSeconds else null,
                )
            })
            exerciseSnapshotId
        }
        workoutNotificationUpdater.refresh()
        return id
    }

    private suspend fun requireActiveExercise(id: Long): SessionExerciseEntity {
        val exercise = sessionExerciseDao.getById(id) ?: error("Session exercise not found.")
        require(workoutSessionDao.getById(exercise.sessionId)?.status == WorkoutSessionStatus.ACTIVE) {
            "Only active workouts can be edited."
        }
        requireNoDurationTimer(workoutSessionDao.getById(exercise.sessionId)!!)
        return exercise
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
            requireNoDurationTimer(session)

            val existingSets = sessionSetDao.getForSessionExercise(sessionExerciseId)
            require(sessionExercise.trackingModeSnapshot != TrackingMode.DURATION || setType == SetType.EXTRA) {
                "Duration exercises support extra timed sets."
            }
            require(setType != SetType.DROP || sessionExercise.trackingModeSnapshot == TrackingMode.WEIGHT_REPS) {
                "Drop sets require weight and reps tracking."
            }
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
                    prescribedWeightCentiKg = if (sessionExercise.trackingModeSnapshot == TrackingMode.WEIGHT_REPS) defaultWeight else null,
                    prescribedReps = if (sessionExercise.trackingModeSnapshot != TrackingMode.DURATION) defaultReps else null,
                    prescribedDurationSeconds = sessionExercise.targetDurationSecondsSnapshot,
                ),
            )
        }
        workoutNotificationUpdater.refresh()
        return setId
    }

    suspend fun replaceExerciseForToday(
        sessionExerciseId: Long,
        replacementExerciseName: String,
    ) {
        val trimmedName = replacementExerciseName.trim()
        require(trimmedName.isNotEmpty()) { "Choose an exercise first." }

        database.withTransaction {
            val sessionExercise = sessionExerciseDao.getById(sessionExerciseId)
                ?: error("Session exercise not found.")
            val session = workoutSessionDao.getById(sessionExercise.sessionId)
                ?: error("Workout session not found.")
            require(session.status == WorkoutSessionStatus.ACTIVE) { "Only active workouts can be edited." }
            requireNoDurationTimer(session)

            sessionExerciseDao.update(
                sessionExercise.copy(
                    sourceWorkoutTemplateExerciseId = null,
                    exerciseNameSnapshot = trimmedName,
                    setupNoteSnapshot = "",
                ),
            )
            sessionSetDao.getForSessionExercise(sessionExerciseId).forEach { set ->
                if (set.countsForProgression) {
                    sessionSetDao.update(set.copy(countsForProgression = false))
                }
            }
        }
        workoutNotificationUpdater.refresh()
    }

    suspend fun uncompleteSet(setId: Long) {
        database.withTransaction {
            val set = sessionSetDao.getById(setId) ?: error("Set not found.")
            requireActiveExercise(set.sessionExerciseId)
            latestUndo = null
            sessionSetDao.update(
                set.copy(
                    actualWeightCentiKg = null,
                    actualReps = null,
                    actualDurationSeconds = null,
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
            requireActiveExercise(set.sessionExerciseId)
            latestUndo = null
            sessionSetDao.update(
                set.copy(
                    actualWeightCentiKg = null,
                    actualReps = null,
                    actualDurationSeconds = null,
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
        durationTimerScheduler.cancel()
        workoutNotificationUpdater.cancel()
    }

    suspend fun finishActiveWorkout(
        allowPartial: Boolean,
        progressionChoices: Map<Long, ProgressionFinishChoice> = emptyMap(),
        expectedWearSessionId: Long? = null,
    ): WorkoutCompletionSummary {
        val summary = database.withTransaction {
            val activeSession = workoutSessionDao.getActive()
                ?: error("No active workout to finish.")
            requireNoDurationTimer(activeSession)
            require(!activeSession.progressionApplied) { "Progression has already been applied." }

            val sessionExercises = sessionExerciseDao.getForSession(activeSession.id)
            val targetsBefore = sessionExercises.associate { it.id to completionTargets(it.sourceWorkoutTemplateExerciseId) }
            val setsByExerciseId = sessionExercises.associate { sessionExercise ->
                sessionExercise.id to sessionSetDao.getForSessionExercise(sessionExercise.id)
            }
            if (expectedWearSessionId != null) {
                require(activeSession.id == expectedWearSessionId) { "Workout changed. Refresh your watch." }
                val sets = setsByExerciseId.values.flatten()
                require(sets.none { it.status == SessionSetStatus.PENDING }) { "There are still sets to log." }
                require(sets.filter { it.isPlanned && it.setType != SetType.WARMUP }
                    .all { it.status == SessionSetStatus.COMPLETED }) { "Finish this partial workout on your phone." }
                require(sets.changedProgressionSets().isEmpty()) { "Review changed targets and finish on your phone." }
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
                            actualDurationSeconds = null,
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
                val currentTemplate = workoutTemplateExerciseDao.getById(sourceTemplateExerciseId) ?: return@forEach
                if (currentTemplate.trackingMode != sessionExercise.trackingModeSnapshot) return@forEach
                if (sessionExercise.trackingModeSnapshot == TrackingMode.DURATION) {
                    val planned = finalSetsByExerciseId.getValue(sessionExercise.id)
                        .filter { it.isPlanned && it.setType == SetType.WORKING }
                    val target = sessionExercise.targetDurationSecondsSnapshot ?: return@forEach
                    // Preserve a target edited in the Program while this workout was active.
                    if (currentTemplate.targetDurationSeconds == target && planned.isNotEmpty() &&
                        planned.all { it.status == SessionSetStatus.COMPLETED &&
                            (it.actualDurationSeconds ?: -1) >= (it.prescribedDurationSeconds ?: target) }) {
                        workoutTemplateExerciseDao.update(currentTemplate.copy(
                            targetDurationSeconds = (target.toLong() + sessionExercise.durationIncrementSecondsSnapshot)
                                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        ))
                    }
                    return@forEach
                }
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
                        trackingMode = sessionExercise.trackingModeSnapshot,
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
            val finalSets = finalSetsByExerciseId.values.flatten()
            val workingSets = finalSets.filter { it.setType == SetType.WORKING }
            WorkoutCompletionSummary(
                sessionId = activeSession.id,
                workoutName = activeSession.workoutNameSnapshot,
                durationSeconds = ((completedAt - activeSession.startedAt) / 1_000L).coerceAtLeast(0),
                partial = !allPlannedSetsCompleted,
                completedWorkingSets = workingSets.count { it.status == SessionSetStatus.COMPLETED },
                totalWorkingSets = workingSets.size,
                skippedSets = finalSets.count { it.status == SessionSetStatus.SKIPPED },
                exercises = sessionExercises.map { exercise ->
                    ExerciseCompletionSummary(
                        sessionExerciseId = exercise.id,
                        name = exercise.exerciseNameSnapshot,
                        before = targetsBefore[exercise.id],
                        after = completionTargets(exercise.sourceWorkoutTemplateExerciseId),
                    )
                },
            )
        }
        _completionSummary.value = summary
        latestUndo = null
        restTimerScheduler.cancel()
        durationTimerScheduler.cancel()
        workoutNotificationUpdater.cancel()
        runCatching { finalizedWorkoutSync.syncAfterFinalization(summary.sessionId) }
        return summary
    }

    // Read effective future targets from Room; never evaluate progression a second time.
    private suspend fun completionTargets(sourceId: Long?): List<CompletionTarget>? {
        sourceId ?: return null
        val template = workoutTemplateExerciseDao.getById(sourceId) ?: return null
        val progression = progressionStateDao.getForTemplateExercise(sourceId) ?: return null
        val overrides = workoutTemplateSetTargetDao.getForTemplateExercise(sourceId).associateBy { it.setOrder }
        return (0 until template.plannedWorkingSets).map { order ->
            CompletionTarget(
                weightCentiKg = overrides[order]?.prescribedWeightCentiKg ?: progression.currentWeightCentiKg,
                reps = overrides[order]?.prescribedReps ?: progression.currentTargetReps,
                trackingMode = template.trackingMode,
                durationSeconds = template.targetDurationSeconds,
            )
        }
    }

    suspend fun addRestTime(seconds: Int) {
        require(seconds > 0) { "Rest time adjustment must be positive." }
        val restEndsAt = database.withTransaction {
            undoRestDeadline = null
            val activeSession = workoutSessionDao.getActive()
                ?: error("No active workout.")
            requireNoDurationTimer(activeSession)
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
            undoRestDeadline = null
            workoutSessionDao.getActive()?.let { activeSession ->
                requireNoDurationTimer(activeSession)
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

    suspend fun syncTimers() {
        syncRestTimerAlarm()
        reconcileDurationTimer()
    }

    private fun requireNoDurationTimer(session: WorkoutSessionEntity) {
        require(session.activeDurationSetId == null) { "Stop or cancel the running duration set first." }
    }

    private fun WorkoutSessionEntity.withoutDurationTimer() = copy(
        activeDurationSetId = null,
        durationStartsAt = null,
        durationEndsAt = null,
    )

    private suspend fun restSecondsAfterCompletedWorkingSet(
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

        val roundHasPendingSets = groupExercises.any { member ->
            sessionSetDao.getForSessionExercise(member.id).any {
                it.setOrder == completedSet.setOrder && it.status == SessionSetStatus.PENDING
            }
        }
        return if (!roundHasPendingSets) {
            sessionExercise.supersetRestSecondsSnapshot ?: sessionExercise.restSecondsSnapshot
        } else {
            0
        }
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
