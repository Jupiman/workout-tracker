package com.jupiman.workouttracker.navigation

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jupiman.workouttracker.di.AppContainer
import com.jupiman.workouttracker.healthconnect.AndroidHealthConnectGateway
import com.jupiman.workouttracker.healthconnect.HealthConnectAvailability
import com.jupiman.workouttracker.healthconnect.HealthConnectSettingsState
import com.jupiman.workouttracker.healthconnect.HealthConnectSyncResult
import com.jupiman.workouttracker.selfhosted.ConnectionTestResult
import com.jupiman.workouttracker.selfhosted.SelfHostedSettingsState
import com.jupiman.workouttracker.selfhosted.displayMessage
import com.jupiman.workouttracker.ui.screen.HistoryScreen
import com.jupiman.workouttracker.ui.screen.ProgramScreen
import com.jupiman.workouttracker.ui.screen.SettingsScreen
import com.jupiman.workouttracker.ui.screen.WorkoutHomeScreen
import com.jupiman.workouttracker.ui.LocalAppPreferences
import com.jupiman.workouttracker.ui.theme.WorkoutGlyph
import com.jupiman.workouttracker.ui.theme.WorkoutIcon
import com.jupiman.workouttracker.ui.viewmodel.AppViewModelFactory
import com.jupiman.workouttracker.ui.viewmodel.HistoryViewModel
import com.jupiman.workouttracker.ui.viewmodel.HomeViewModel
import com.jupiman.workouttracker.ui.viewmodel.ProgramViewModel
import java.time.LocalDate
import kotlinx.coroutines.launch

private const val SETTINGS_ROUTE = "settings"
private const val MAIN_ROUTE = "main"

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTrackerApp(
    container: AppContainer,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val viewModelFactory = AppViewModelFactory(container)
    val destinations = WorkoutTrackerDestination.entries
    val pagerState = rememberPagerState(pageCount = { destinations.size })
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = destinations[pagerState.currentPage]
    val preferences = LocalAppPreferences.current
    var appMenuExpanded by remember { mutableStateOf(false) }
    var createProgramRequested by rememberSaveable { mutableStateOf(false) }
    var pendingProgramExportId by rememberSaveable { mutableStateOf<Long?>(null) }
    var importedProgramId by rememberSaveable { mutableStateOf<Long?>(null) }
    var healthConnectState by remember { mutableStateOf<HealthConnectSettingsState?>(null) }
    var healthConnectBusy by remember { mutableStateOf(false) }
    var selfHostedState by remember { mutableStateOf<SelfHostedSettingsState?>(null) }
    var selfHostedBusy by remember { mutableStateOf(false) }
    var selfHostedConnectionStatus by remember { mutableStateOf<String?>(null) }
    val versionName = remember(context) {
        @Suppress("DEPRECATION")
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    fun refreshHealthConnectState() {
        scope.launch {
            healthConnectState = container.healthConnectSyncManager.settingsState()
        }
    }
    fun refreshSelfHostedState() {
        scope.launch { selfHostedState = container.selfHostedSyncManager.settingsState() }
    }
    fun showHealthConnectResult(result: HealthConnectSyncResult) {
        showToast(
            when (result) {
                is HealthConnectSyncResult.Completed -> "Health Connect sync completed."
                HealthConnectSyncResult.PermissionRequired -> "Health Connect permission is required."
                HealthConnectSyncResult.ProviderUpdateRequired -> "Health Connect must be installed or updated."
                HealthConnectSyncResult.Unavailable -> "Health Connect is not available on this device."
                HealthConnectSyncResult.Failed -> "Health Connect sync could not be completed."
                HealthConnectSyncResult.Disabled -> "Health Connect sync is disabled."
            },
        )
    }
    val healthConnectPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { grantedPermissions ->
        scope.launch {
            if (AndroidHealthConnectGateway.WRITE_EXERCISE_PERMISSION in grantedPermissions) {
                container.appPreferencesRepository.setHealthConnectSyncEnabled(true)
                showHealthConnectResult(container.healthConnectSyncManager.syncAllFinalizedWorkouts())
            }
            healthConnectState = container.healthConnectSyncManager.settingsState()
        }
    }

    LaunchedEffect(currentRoute, preferences.healthConnectSyncEnabled) {
        if (currentRoute == SETTINGS_ROUTE) {
            healthConnectState = container.healthConnectSyncManager.settingsState()
            selfHostedState = container.selfHostedSyncManager.settingsState()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshHealthConnectState()
                refreshSelfHostedState()
                scope.launch { container.selfHostedSyncManager.schedulePendingIfConfigured() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                container.workoutSessionRepository.syncTimers()
                container.workoutNotificationCoordinator.refresh()
                container.healthConnectSyncManager.syncIfEnabled()
            }.onSuccess {
                showToast("Backup restored.")
            }.onFailure { throwable ->
                showToast(throwable.message ?: "Backup restore failed.")
            }
        }
    }
    val exportProgramLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val programId = pendingProgramExportId
        pendingProgramExportId = null
        if (uri == null || programId == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val outputStream = context.contentResolver.openOutputStream(uri)
                    ?: error("Could not open program file.")
                outputStream.use { stream ->
                    container.programTransferRepository.exportProgram(programId, stream)
                }
            }.onSuccess {
                showToast("Program exported.")
            }.onFailure { throwable ->
                showToast(throwable.message ?: "Program export failed.")
            }
        }
    }
    val importProgramLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: error("Could not open program file.")
                inputStream.use { stream ->
                    container.programTransferRepository.importProgram(stream)
                }
            }.onSuccess { importedProgram ->
                importedProgramId = importedProgram.id
                showToast("${importedProgram.name} imported.")
            }.onFailure { throwable ->
                showToast(throwable.message ?: "Program import failed.")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (currentRoute == SETTINGS_ROUTE) {
                            "Settings"
                        } else {
                            currentDestination.label
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (currentRoute == SETTINGS_ROUTE) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            WorkoutGlyph(WorkoutIcon.Back, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (currentRoute != SETTINGS_ROUTE) Box {
                        IconButton(
                            onClick = { appMenuExpanded = true },
                        ) { WorkoutGlyph(WorkoutIcon.Menu, contentDescription = "App menu") }
                        DropdownMenu(expanded = appMenuExpanded, onDismissRequest = { appMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("Settings") }, onClick = {
                                appMenuExpanded = false
                                navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true }
                            })
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            )
        },
        bottomBar = {
            if (currentRoute != SETTINGS_ROUTE) NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                destinations.forEach { destination ->
                    val page = destinations.indexOf(destination)
                    NavigationBarItem(
                        selected = pagerState.currentPage == page,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(page)
                            }
                        },
                        label = { Text(destination.label) },
                        icon = {
                            WorkoutGlyph(
                                icon = when (destination) {
                                    WorkoutTrackerDestination.Workout -> WorkoutIcon.Workout
                                    WorkoutTrackerDestination.Program -> WorkoutIcon.Program
                                    WorkoutTrackerDestination.History -> WorkoutIcon.History
                                },
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MAIN_ROUTE,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(MAIN_ROUTE) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { page -> destinations[page].route },
                ) { page ->
                    when (destinations[page]) {
                        WorkoutTrackerDestination.Workout -> {
                            val homeViewModel: HomeViewModel = viewModel(factory = viewModelFactory)
                            WorkoutHomeScreen(
                                viewModel = homeViewModel,
                                onCreateProgram = {
                                    createProgramRequested = true
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            destinations.indexOf(WorkoutTrackerDestination.Program),
                                        )
                                    }
                                },
                                onOpenProgram = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            destinations.indexOf(WorkoutTrackerDestination.Program),
                                        )
                                    }
                                },
                            )
                        }
                        WorkoutTrackerDestination.Program -> {
                            val programViewModel: ProgramViewModel = viewModel(factory = viewModelFactory)
                            ProgramScreen(
                                viewModel = programViewModel,
                                backHandlerEnabled = pagerState.currentPage == page,
                                createProgramRequested = createProgramRequested,
                                onCreateProgramRequestHandled = { createProgramRequested = false },
                                onOpenWorkout = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(
                                            destinations.indexOf(WorkoutTrackerDestination.Workout),
                                        )
                                    }
                                },
                                onExportProgram = { program ->
                                    pendingProgramExportId = program.id
                                    exportProgramLauncher.launch(programExportFileName(program.name))
                                },
                                onImportProgram = {
                                    importProgramLauncher.launch(
                                        arrayOf("application/json", "text/json", "application/octet-stream", "*/*"),
                                    )
                                },
                                importedProgramId = importedProgramId,
                                onImportedProgramHandled = { importedProgramId = null },
                            )
                        }
                        WorkoutTrackerDestination.History -> {
                            val historyViewModel: HistoryViewModel = viewModel(factory = viewModelFactory)
                            HistoryScreen(
                                viewModel = historyViewModel,
                                backHandlerEnabled = pagerState.currentPage == page,
                            )
                        }
                    }
                }
            }
            composable(SETTINGS_ROUTE) {
                SettingsScreen(
                    versionName = versionName,
                    preferences = preferences,
                    onWeightUnitChange = { scope.launch { container.appPreferencesRepository.setWeightUnit(it) } },
                    onThemeModeChange = { scope.launch { container.appPreferencesRepository.setThemeMode(it) } },
                    onDurationPrepSecondsChange = { scope.launch { container.appPreferencesRepository.setDurationPrepSeconds(it) } },
                    onKeepPhoneScreenAwakeChange = { scope.launch { container.appPreferencesRepository.setKeepPhoneScreenAwake(it) } },
                    onRestCompletionPhoneAlertChange = { scope.launch { container.appPreferencesRepository.setRestCompletionPhoneAlert(it) } },
                    onDurationCompletionPhoneAlertChange = { scope.launch { container.appPreferencesRepository.setDurationCompletionPhoneAlert(it) } },
                    healthConnectState = healthConnectState,
                    healthConnectBusy = healthConnectBusy,
                    onHealthConnectSyncEnabledChange = { enabled ->
                        if (!enabled) {
                            scope.launch { container.appPreferencesRepository.setHealthConnectSyncEnabled(false) }
                        } else {
                            scope.launch {
                                val state = container.healthConnectSyncManager.settingsState()
                                healthConnectState = state
                                when {
                                    state.availability != HealthConnectAvailability.AVAILABLE -> Unit
                                    state.hasWritePermission -> {
                                        container.appPreferencesRepository.setHealthConnectSyncEnabled(true)
                                        container.healthConnectSyncManager.syncAllFinalizedWorkouts()
                                    }
                                    else -> healthConnectPermissionLauncher.launch(
                                        setOf(AndroidHealthConnectGateway.WRITE_EXERCISE_PERMISSION),
                                    )
                                }
                            }
                        }
                    },
                    onConnectHealthConnect = {
                        healthConnectPermissionLauncher.launch(
                            setOf(AndroidHealthConnectGateway.WRITE_EXERCISE_PERMISSION),
                        )
                    },
                    onSyncHealthConnectNow = {
                        scope.launch {
                            healthConnectBusy = true
                            val result = container.healthConnectSyncManager.syncAllFinalizedWorkouts()
                            healthConnectBusy = false
                            showHealthConnectResult(result)
                            refreshHealthConnectState()
                        }
                    },
                    onManageHealthConnect = {
                        runCatching {
                            context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
                        }.onFailure { showToast("Health Connect settings could not be opened.") }
                    },
                    onInstallHealthConnect = {
                        val provider = AndroidHealthConnectGateway.PROVIDER_PACKAGE_NAME
                        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$provider"))
                        runCatching { context.startActivity(marketIntent) }.onFailure {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$provider")),
                            )
                        }
                    },
                    selfHostedState = selfHostedState,
                    allowSelfHostedHttp = container.selfHostedTransportPolicy.allowsHttp,
                    selfHostedBusy = selfHostedBusy,
                    selfHostedConnectionStatus = selfHostedConnectionStatus,
                    onSaveSelfHostedConfiguration = { serverAddress, useHttps, token ->
                        scope.launch {
                            selfHostedBusy = true
                            val result = container.selfHostedSyncManager.saveConfiguration(serverAddress, useHttps, token)
                            if (result.isSuccess) selfHostedConnectionStatus = null
                            selfHostedBusy = false
                            showToast(result.fold({ "Self-hosted sync configuration saved." }, { it.message ?: "Configuration is invalid." }))
                            refreshSelfHostedState()
                        }
                    },
                    onTestSelfHostedConnection = { serverAddress, useHttps, token ->
                        scope.launch {
                            selfHostedBusy = true
                            val result = runCatching {
                                container.selfHostedSyncManager.testConnection(serverAddress, useHttps, token)
                            }.getOrElse { ConnectionTestResult.Unexpected("Connection test failed.") }
                            selfHostedBusy = false
                            selfHostedConnectionStatus = result.displayMessage()
                            showToast(result.displayMessage())
                            refreshSelfHostedState()
                        }
                    },
                    onSelfHostedSyncEnabledChange = { enabled, serverAddress, useHttps, token ->
                        scope.launch {
                            selfHostedBusy = true
                            val saveResult = if (enabled) {
                                container.selfHostedSyncManager.saveConfiguration(serverAddress, useHttps, token)
                            } else {
                                Result.success(Unit)
                            }
                            val result = saveResult.fold(
                                onSuccess = { container.selfHostedSyncManager.setEnabled(enabled) },
                                onFailure = { Result.failure(it) },
                            )
                            selfHostedBusy = false
                            result.exceptionOrNull()?.let { showToast(it.message ?: "Self-hosted sync could not be updated.") }
                            refreshSelfHostedState()
                        }
                    },
                    onSyncSelfHostedNow = {
                        scope.launch {
                            selfHostedBusy = true
                            val result = runCatching { container.selfHostedSyncManager.syncAll() }
                            selfHostedBusy = false
                            showToast(result.fold(
                                onSuccess = { "${it.synchronized} workouts synchronized; ${it.failed} failed." },
                                onFailure = { "Self-hosted sync could not be completed." },
                            ))
                            refreshSelfHostedState()
                        }
                    },
                    onOpenNotificationSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        )
                    },
                    onOpenPrivacyPolicy = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://jupiman.cz/policy.html")),
                        )
                    },
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

private fun programExportFileName(programName: String): String {
    val safeName = programName
        .replace(Regex("[\\\\/:*?\"<>|]"), "-")
        .trim()
        .ifEmpty { "program" }
    return "workout-companion-$safeName-${LocalDate.now()}.json"
}
