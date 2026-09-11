package com.jupiman.workouttracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.jupiman.workouttracker.di.AppContainer

class AppViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    programRepository = container.programRepository,
                    workoutSessionRepository = container.workoutSessionRepository,
                ) as T
            }
            modelClass.isAssignableFrom(ProgramViewModel::class.java) -> {
                ProgramViewModel(
                    programRepository = container.programRepository,
                    exerciseRepository = container.exerciseRepository,
                ) as T
            }
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> {
                HistoryViewModel(workoutSessionRepository = container.workoutSessionRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
