package com.jupiman.workouttracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutTemplateEntity
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import com.jupiman.workouttracker.data.repository.ProgramRepository
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
        programRepository.firstActiveProgramTemplate,
        programRepository.activeProgramTemplates,
    ) { activeProgram, activeWorkout, nextTemplate, activeProgramTemplates ->
        HomeUiState(
            activeProgram = activeProgram,
            activeWorkout = activeWorkout,
            nextWorkoutName = nextTemplate?.name,
            nextWorkoutTemplateId = nextTemplate?.id,
            activeProgramTemplates = activeProgramTemplates,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

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
