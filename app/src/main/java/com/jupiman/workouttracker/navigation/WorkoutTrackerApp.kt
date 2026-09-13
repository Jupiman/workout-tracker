package com.jupiman.workouttracker.navigation

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun WorkoutTrackerApp(
    container: AppContainer,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val viewModelFactory = AppViewModelFactory(container)
    val destinations = WorkoutTrackerDestination.entries
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val outputStream = context.contentResolver.openOutputStream(uri)
                    ?: error("Could not open backup file.")
                outputStream.use { stream ->
                    container.dataBackupRepository.exportBackup(stream)
                }
            }.onSuccess {
                showToast("Backup exported.")
            }.onFailure { throwable ->
                showToast(throwable.message ?: "Backup export failed.")
            }
        }
    }
    val restoreBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: error("Could not open backup file.")
                inputStream.use { stream ->
                    container.dataBackupRepository.restoreBackup(stream)
                }
                container.workoutSessionRepository.syncRestTimerAlarm()
                container.workoutNotificationCoordinator.refresh()
            }.onSuccess {
                showToast("Backup restored.")
            }.onFailure { throwable ->
                showToast(throwable.message ?: "Backup restore failed.")
            }
        }
    }

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
                HistoryScreen(
                    viewModel = historyViewModel,
                    onExportBackup = {
                        exportBackupLauncher.launch(
                            "workout-companion-backup-${LocalDate.now()}.json",
                        )
                    },
                    onRestoreBackup = {
                        restoreBackupLauncher.launch(
                            arrayOf("application/json", "text/json", "application/octet-stream", "*/*"),
                        )
                    },
                )
            }
        }
    }
}
