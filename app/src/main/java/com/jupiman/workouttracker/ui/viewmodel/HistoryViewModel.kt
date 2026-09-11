package com.jupiman.workouttracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiman.workouttracker.data.local.model.WorkoutSessionWithDetails
import com.jupiman.workouttracker.data.repository.WorkoutSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HistoryUiState(
    val sessions: List<WorkoutSessionWithDetails> = emptyList(),
    val selectedSession: WorkoutSessionWithDetails? = null,
)

class HistoryViewModel(
    workoutSessionRepository: WorkoutSessionRepository,
) : ViewModel() {
    private val selectedSessionId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<HistoryUiState> = combine(
        workoutSessionRepository.historyWithDetails,
        selectedSessionId,
    ) { sessions, selectedId ->
        HistoryUiState(
            sessions = sessions,
            selectedSession = selectedId?.let { id -> sessions.firstOrNull { it.session.id == id } },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState(),
    )

    fun selectSession(sessionId: Long) {
        selectedSessionId.value = sessionId
    }

    fun closeDetails() {
        selectedSessionId.value = null
    }
}
