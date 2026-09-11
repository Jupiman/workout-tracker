package com.jupiman.workouttracker.data.repository

import androidx.room.withTransaction
import com.jupiman.workouttracker.data.local.WorkoutTrackerDatabase
import com.jupiman.workouttracker.data.local.dao.ProgramDao
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
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails

class WorkoutSessionRepository(
    private val database: WorkoutTrackerDatabase,
    private val workoutSessionDao: WorkoutSessionDao,
    private val sessionExerciseDao: SessionExerciseDao,
    private val sessionSetDao: SessionSetDao,
    private val programDao: ProgramDao,
    private val workoutTemplateDao: WorkoutTemplateDao,
    private val workoutTemplateExerciseDao: WorkoutTemplateExerciseDao,
    private val supersetGroupDao: SupersetGroupDao,
) {
    val activeSession = workoutSessionDao.observeActive()
    val activeSessionWithDetails = workoutSessionDao.observeActiveWithDetails()
    val history = workoutSessionDao.observeHistory()

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

        database.withTransaction {
            val set = sessionSetDao.getById(setId) ?: error("Set not found.")
            sessionSetDao.update(
                set.copy(
                    actualWeightCentiKg = actualWeightCentiKg,
                    actualReps = actualReps,
                    status = SessionSetStatus.COMPLETED,
                    completedAt = System.currentTimeMillis(),
                ),
            )
        }
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
    }
}
