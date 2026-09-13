package com.jupiman.workouttracker.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jupiman.workouttracker.di.AppContainer
import com.jupiman.workouttracker.ui.screen.HistoryScreen
import com.jupiman.workouttracker.ui.screen.ProgramScreen
import com.jupiman.workouttracker.ui.screen.WorkoutHomeScreen
import com.jupiman.workouttracker.ui.viewmodel.AppViewModelFactory
import com.jupiman.workouttracker.ui.viewmodel.HistoryViewModel
import com.jupiman.workouttracker.ui.viewmodel.HomeViewModel
import com.jupiman.workouttracker.ui.viewmodel.ProgramViewModel

@Composable
fun WorkoutTrackerApp(
    container: AppContainer,
) {
    val navController = rememberNavController()
    val viewModelFactory = AppViewModelFactory(container)
    val destinations = WorkoutTrackerDestination.entries
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        label = { Text(destination.label) },
                        icon = {},
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = WorkoutTrackerDestination.Workout.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(WorkoutTrackerDestination.Workout.route) {
                val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
                WorkoutHomeScreen(viewModel = homeViewModel)
            }
            composable(WorkoutTrackerDestination.Program.route) {
                val programViewModel: ProgramViewModel = viewModel(factory = viewModelFactory)
                ProgramScreen(viewModel = programViewModel)
            }
            composable(WorkoutTrackerDestination.History.route) {
                val historyViewModel: HistoryViewModel = viewModel(factory = viewModelFactory)
                HistoryScreen(viewModel = historyViewModel)
            }
        }
    }
}
