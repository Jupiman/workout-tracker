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
import com.jupiman.workouttracker.data.local.entity.SessionExerciseEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import com.jupiman.workouttracker.domain.progression.ProgressionConfig
import com.jupiman.workouttracker.domain.progression.ProgressionEngine
import com.jupiman.workouttracker.domain.progression.ProgressionSet
import com.jupiman.workouttracker.notification.RestTimerScheduler
import kotlin.math.max

class WorkoutSessionRepository(
    private val database: WorkoutTrackerDatabase,
    private val workoutSessionDao: WorkoutSessionDao,
    private val sessionExerciseDao: SessionExerciseDao,
    private val sessionSetDao: SessionSetDao,
    private val programDao: ProgramDao,
    private val workoutTemplateDao: WorkoutTemplateDao,
    private val workoutTemplateExerciseDao: WorkoutTemplateExerciseDao,
    private val progressionStateDao: ProgressionStateDao,
    private val supersetGroupDao: SupersetGroupDao,
    private val restTimerScheduler: RestTimerScheduler,
) {
    val activeSession = workoutSessionDao.observeActive()
    val activeSessionWithDetails = workoutSessionDao.observeActiveWithDetails()
    val latestFinishedSessionForActiveProgram = workoutSessionDao.observeLatestFinishedForActiveProgram()
    val history = workoutSessionDao.observeHistory()
    val historyWithDetails = workoutSessionDao.observeHistoryWithDetails()

    fun sessionExercises(sessionId: Long) = sessionExerciseDao.observeForSession(sessionId)

    fun sessionSets(sessionExerciseId: Long) = sessionSetDao.observeForSessionExercise(sessionExerciseId)

    suspend fun startWorkout(workoutTemplateId: Long): Long = database.withTransaction {
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

            val plannedSets = (0 until templateExercise.plannedWorkingSets).map { setIndex ->
                SessionSetEntity(
                    sessionExerciseId = sessionExerciseId,
                    setOrder = setIndex,
                    setType = SetType.WORKING,
                    isPlanned = true,
                    countsForProgression = true,
                    prescribedWeightCentiKg = templateExercise.currentWeightCentiKg,
                    prescribedReps = templateExercise.currentTargetReps,
                )
            }
            sessionSetDao.insertAll(plannedSets)
        }

        sessionId
    }

    suspend fun completeSet(
        setId: Long,
        actualWeightCentiKg: Int,
        actualReps: Int,
    ) {
        require(actualWeightCentiKg >= 0) { "Weight cannot be negative." }
        require(actualReps >= 0) { "Reps cannot be negative." }

        var restEndsAtToSchedule: Long? = null
        var shouldCancelRest = false
        database.withTransaction {
            val set = sessionSetDao.getById(setId) ?: error("Set not found.")
            val sessionExercise = sessionExerciseDao.getById(set.sessionExerciseId)
                ?: error("Session exercise not found.")
            val session = workoutSessionDao.getById(sessionExercise.sessionId)
                ?: error("Workout session not found.")
            require(session.status == WorkoutSessionStatus.ACTIVE) { "Only active workouts can be edited." }
            val now = System.currentTimeMillis()
            sessionSetDao.update(
                set.copy(
                    actualWeightCentiKg = actualWeightCentiKg,
                    actualReps = actualReps,
                    status = SessionSetStatus.COMPLETED,
                    completedAt = now,
                ),
            )

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
    }

    suspend fun addSessionSet(
        sessionExerciseId: Long,
        setType: SetType,
    ): Long = database.withTransaction {
        require(setType != SetType.WORKING) { "Only session-only sets can be added." }
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
            SetType.WORKING -> error("Working sets are generated from templates.")
        }
        val defaultReps = when (setType) {
            SetType.EXTRA -> sessionExercise.targetRepsSnapshot
            SetType.AMRAP,
            SetType.DROP -> null
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
    }

    suspend fun discardActiveWorkout() {
        database.withTransaction {
            workoutSessionDao.getActive()?.let { activeSession ->
                workoutSessionDao.deleteActiveById(activeSession.id)
            }
        }
        restTimerScheduler.cancel()
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
                .filter { it.isPlanned }
                .all { it.status == SessionSetStatus.COMPLETED }

            if (!allPlannedSetsCompleted && !allowPartial) {
                throw IllegalStateException("Some planned sets are incomplete.")
            }

            sessionExercises.forEach { sessionExercise ->
                val sourceTemplateExerciseId = sessionExercise.sourceWorkoutTemplateExerciseId
                    ?: return@forEach
                val progressionState = progressionStateDao.getForTemplateExercise(sourceTemplateExerciseId)
                    ?: return@forEach
                val exerciseSets = setsByExerciseId.getValue(sessionExercise.id)
                when (progressionChoices[sessionExercise.id] ?: ProgressionFinishChoice.AUTOMATIC) {
                    ProgressionFinishChoice.NO_PROGRESSION -> return@forEach
                    ProgressionFinishChoice.SET_TARGET_FROM_LOGGED -> {
                        val target = exerciseSets.loggedProgressionTarget(sessionExercise)
                        progressionStateDao.update(
                            progressionState.copy(
                                currentWeightCentiKg = target.weightCentiKg,
                                currentTargetReps = target.targetReps,
                                updatedAt = System.currentTimeMillis(),
                            ),
                        )
                        return@forEach
                    }
                    ProgressionFinishChoice.AUTOMATIC -> Unit
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
                    completedAt = System.currentTimeMillis(),
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
    }

    suspend fun skipRest() {
        database.withTransaction {
            workoutSessionDao.getActive()?.let { activeSession ->
                workoutSessionDao.update(activeSession.copy(restEndsAt = null))
            }
        }
        restTimerScheduler.cancel()
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

    private fun List<SessionSetEntity>.loggedProgressionTarget(
        sessionExercise: SessionExerciseEntity,
    ): LoggedProgressionTarget {
        val bestSet = filter {
            it.countsForProgression &&
                it.status == SessionSetStatus.COMPLETED &&
                it.actualWeightCentiKg != null &&
                it.actualReps != null
        }.maxWithOrNull(
            compareBy<SessionSetEntity> { it.actualWeightCentiKg ?: 0 }
                .thenBy { it.actualReps ?: 0 }
                .thenBy { it.setOrder },
        ) ?: error("No completed planned set can become the next target.")

        return LoggedProgressionTarget(
            weightCentiKg = bestSet.actualWeightCentiKg ?: sessionExercise.prescribedWeightCentiKgSnapshot,
            targetReps = (bestSet.actualReps ?: sessionExercise.targetRepsSnapshot)
                .coerceIn(sessionExercise.repMinSnapshot, sessionExercise.repMaxSnapshot),
        )
    }

    private data class LoggedProgressionTarget(
        val weightCentiKg: Int,
        val targetReps: Int,
    )
}
