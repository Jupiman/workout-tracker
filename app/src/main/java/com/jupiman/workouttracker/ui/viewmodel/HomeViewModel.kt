package com.jupiman.workouttracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiman.workouttracker.data.local.entity.ProgramEntity
import com.jupiman.workouttracker.data.local.entity.WorkoutSessionEntity
import com.jupiman.workouttracker.data.repository.ProgramRepository
import com.jupiman.workouttracker.data.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val activeProgram: ProgramEntity? = null,
    val activeSession: WorkoutSessionEntity? = null,
)

class HomeViewModel(
    programRepository: ProgramRepository,
    workoutSessionRepository: WorkoutSessionRepository,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        programRepository.activeProgram,
        workoutSessionRepository.activeSession,
    ) { activeProgram, activeSession ->
        HomeUiState(
            activeProgram = activeProgram,
            activeSession = activeSession,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}

