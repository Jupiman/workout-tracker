package com.jupiman.workouttracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import com.jupiman.workouttracker.data.local.entity.SessionSetStatus
import com.jupiman.workouttracker.data.local.entity.SetType
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionStatus
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import com.jupiman.workouttracker.data.repository.ProgramRepository
import com.jupiman.workouttracker.data.repository.ProgressionFinishChoice
import com.jupiman.workouttracker.data.repository.WorkoutSessionRepository
import com.jupiman.workouttracker.data.repository.parseCentiKg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val activeProgram: ProgramEntity? = null,
    val activeWorkout: WorkoutSessionWithDetails? = null,
    val nextWorkoutName: String? = null,
    val nextWorkoutTemplateId: Long? = null,
    val activeProgramTemplates: List<WorkoutTemplateEntity> = emptyList(),
    val lastTimeByTemplateExerciseId: Map<Long, LastTimeExerciseContext> = emptyMap(),
)

data class LastTimeExerciseContext(
    val completedAt: Long,
    val workoutName: String,
    val status: WorkoutSessionStatus,
    val sets: List<LastTimeSetContext>,
)

data class LastTimeSetContext(
    val setType: SetType,
    val setOrder: Int,
    val status: SessionSetStatus,
    val weightCentiKg: Int?,
    val reps: Int?,
)

class HomeViewModel(
    private val programRepository: ProgramRepository,
    private val workoutSessionRepository: WorkoutSessionRepository,
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val uiState: StateFlow<HomeUiState> = combine(
        programRepository.activeProgram,
        workoutSessionRepository.activeSessionWithDetails,
        programRepository.activeProgramTemplates,
        workoutSessionRepository.latestFinishedSessionForActiveProgram,
        workoutSessionRepository.historyWithDetails,
    ) { activeProgram, activeWorkout, activeProgramTemplates, latestFinishedSession, history ->
        val nextTemplate = recommendNextWorkoutTemplate(
            templates = activeProgramTemplates,
            latestFinishedSession = latestFinishedSession,
        )
        HomeUiState(
            activeProgram = activeProgram,
            activeWorkout = activeWorkout,
            nextWorkoutName = nextTemplate?.name,
            nextWorkoutTemplateId = nextTemplate?.id,
            activeProgramTemplates = activeProgramTemplates,
            lastTimeByTemplateExerciseId = lastTimeContextsForActiveWorkout(
                activeWorkout = activeWorkout,
                history = history,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    init {
        viewModelScope.launch {
            workoutSessionRepository.syncRestTimerAlarm()
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun startWorkout(workoutTemplateId: Long?) = launchOperation("Workout started.") {
        require(workoutTemplateId != null) { "Choose a workout first." }
        workoutSessionRepository.startWorkout(workoutTemplateId)
    }

    fun completeSet(
        setId: Long,
        actualWeight: String,
        actualReps: String,
    ) = launchOperation("Set logged.") {
        val reps = actualReps.trim().toIntOrNull()
            ?: throw IllegalArgumentException("Reps must be a whole number.")
        require(reps >= 0) { "Reps cannot be negative." }

        workoutSessionRepository.completeSet(
            setId = setId,
            actualWeightCentiKg = parseCentiKg(actualWeight),
            actualReps = reps,
        )
    }

    fun uncompleteSet(setId: Long) = launchOperation("Set marked pending.") {
        workoutSessionRepository.uncompleteSet(setId)
    }

    fun skipSet(setId: Long) = launchOperation("Set skipped.") {
        workoutSessionRepository.skipSet(setId)
    }

    fun discardActiveWorkout() = launchOperation("Workout discarded.") {
        workoutSessionRepository.discardActiveWorkout()
    }

    fun finishActiveWorkout(
        allowPartial: Boolean,
        progressionChoices: Map<Long, ProgressionFinishChoice> = emptyMap(),
    ) = launchOperation("Workout finished.") {
        workoutSessionRepository.finishActiveWorkout(
            allowPartial = allowPartial,
            progressionChoices = progressionChoices,
        )
    }

    fun addRestTime(seconds: Int) = launchOperation("Rest extended.") {
        workoutSessionRepository.addRestTime(seconds)
    }

    fun skipRest() = launchOperation("Rest skipped.") {
        workoutSessionRepository.skipRest()
    }

    fun addSessionSet(sessionExerciseId: Long, setType: SetType) = launchOperation("Set added.") {
        workoutSessionRepository.addSessionSet(
            sessionExerciseId = sessionExerciseId,
            setType = setType,
        )
    }

    private fun launchOperation(
        successMessage: String,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { _message.value = successMessage }
                .onFailure { throwable ->
                    _message.value = throwable.message ?: "Something went wrong."
                }
        }
    }
}

internal fun lastTimeContextsForActiveWorkout(
    activeWorkout: WorkoutSessionWithDetails?,
    history: List<WorkoutSessionWithDetails>,
): Map<Long, LastTimeExerciseContext> {
    activeWorkout ?: return emptyMap()

    val sortedHistory = history
        .filter { it.session.id != activeWorkout.session.id }
        .sortedByDescending { it.session.completedAt ?: it.session.startedAt }

    return activeWorkout.exercises
        .mapNotNull { it.exercise.sourceWorkoutTemplateExerciseId }
        .distinct()
        .mapNotNull { sourceTemplateExerciseId ->
            val previous = sortedHistory.firstNotNullOfOrNull { historicalSession ->
                historicalSession.exercises.firstOrNull { historicalExercise ->
                    historicalExercise.exercise.sourceWorkoutTemplateExerciseId == sourceTemplateExerciseId
                }?.let { historicalExercise -> historicalSession to historicalExercise }
            } ?: return@mapNotNull null

            val (sessionDetails, exerciseDetails) = previous
            val sets = exerciseDetails.sets
                .filter { it.setType != SetType.WARMUP }
                .sortedBy { it.setOrder }
                .map { set ->
                    LastTimeSetContext(
                        setType = set.setType,
                        setOrder = set.setOrder,
                        status = set.status,
                        weightCentiKg = set.actualWeightCentiKg ?: set.prescribedWeightCentiKg,
                        reps = set.actualReps ?: set.prescribedReps,
                    )
                }

            sourceTemplateExerciseId to LastTimeExerciseContext(
                completedAt = sessionDetails.session.completedAt ?: sessionDetails.session.startedAt,
                workoutName = sessionDetails.session.workoutNameSnapshot,
                status = sessionDetails.session.status,
                sets = sets,
            )
        }
        .toMap()
}

internal fun recommendNextWorkoutTemplate(
    templates: List<WorkoutTemplateEntity>,
    latestFinishedSession: WorkoutSessionEntity?,
): WorkoutTemplateEntity? {
    if (templates.isEmpty()) return null

    val previousTemplateId = latestFinishedSession?.sourceWorkoutTemplateId
    val previousIndex = templates.indexOfFirst { it.id == previousTemplateId }
    if (previousIndex == -1) return templates.first()

    return templates[(previousIndex + 1) % templates.size]
}
