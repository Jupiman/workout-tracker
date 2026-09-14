package com.jupiman.workouttracker.data.repository

import com.jupiman.workouttracker.data.local.dao.WorkoutSessionDao
import com.jupiman.workouttracker.data.local.dao.WorkoutTemplateExerciseDao
import com.jupiman.workouttracker.data.local.entity.SessionSetEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.TrackingMode
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import kotlinx.coroutines.flow.map

data class ExerciseProgressSession(
    val sessionId: Long,
    val completedAt: Long,
    val workoutName: String,
    val status: WorkoutSessionStatus,
    val trackingMode: TrackingMode,
    val sets: List<SessionSetEntity>,
    val graphValue: Int?,
)

class ExerciseProgressRepository(
    private val workoutTemplateExerciseDao: WorkoutTemplateExerciseDao,
    private val workoutSessionDao: WorkoutSessionDao,
) {
    fun tracksForExercise(exerciseId: Long) =
        workoutTemplateExerciseDao.observeProgressTracksForExercise(exerciseId)

    fun sessionsForTrack(workoutTemplateExerciseId: Long) =
        workoutSessionDao.observeHistoryWithDetails().map { sessions ->
            buildExerciseProgressSessions(sessions, workoutTemplateExerciseId)
        }
}

internal fun buildExerciseProgressSessions(
    history: List<WorkoutSessionWithDetails>,
    workoutTemplateExerciseId: Long,
): List<ExerciseProgressSession> = history.mapNotNull { workout ->
    val exercise = workout.exercises.firstOrNull {
        it.exercise.sourceWorkoutTemplateExerciseId == workoutTemplateExerciseId
    } ?: return@mapNotNull null
    val completedAt = workout.session.completedAt ?: return@mapNotNull null
    val trackingMode = exercise.exercise.trackingModeSnapshot
    val qualifyingSets = exercise.sets.filter { set ->
        set.setType == SetType.WORKING &&
            set.isPlanned &&
            set.status == SessionSetStatus.COMPLETED &&
            (trackingMode == TrackingMode.DURATION || set.countsForProgression)
    }
    val graphValue = when (trackingMode) {
        TrackingMode.WEIGHT_REPS -> qualifyingSets.mapNotNull(SessionSetEntity::actualWeightCentiKg).maxOrNull()
        TrackingMode.REPS -> qualifyingSets.mapNotNull(SessionSetEntity::actualReps).maxOrNull()
        TrackingMode.DURATION -> qualifyingSets.mapNotNull(SessionSetEntity::actualDurationSeconds).maxOrNull()
    }
    ExerciseProgressSession(
        sessionId = workout.session.id,
        completedAt = completedAt,
        workoutName = workout.session.workoutNameSnapshot,
        status = workout.session.status,
        trackingMode = trackingMode,
        sets = exercise.sets.sortedBy { it.setOrder },
        graphValue = graphValue,
    )
}.sortedByDescending(ExerciseProgressSession::completedAt)
