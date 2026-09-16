package com.jupiman.workouttracker.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.jupiman.workouttracker.preferences.AppPreferences
import com.jupiman.workouttracker.preferences.ThemeMode
import com.jupiman.workouttracker.preferences.WeightUnit
import com.jupiman.workouttracker.healthconnect.HealthConnectAvailability
import com.jupiman.workouttracker.healthconnect.HealthConnectSettingsState
import com.jupiman.workouttracker.ui.theme.WorkoutSpacing
import com.jupiman.workouttracker.selfhosted.SelfHostedSettingsState
import com.jupiman.workouttracker.selfhosted.ServerAddressValidation
import com.jupiman.workouttracker.selfhosted.formatSelfHostedLastSync
import com.jupiman.workouttracker.selfhosted.validateServerAddress

private enum class ChoiceSetting { WEIGHT_UNIT, THEME, DURATION_PREP }

@Composable
fun SettingsScreen(
    versionName: String?,
    preferences: AppPreferences,
    onWeightUnitChange: (WeightUnit) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDurationPrepSecondsChange: (Int) -> Unit,
    onKeepPhoneScreenAwakeChange: (Boolean) -> Unit,
    onRestCompletionPhoneAlertChange: (Boolean) -> Unit,
    onDurationCompletionPhoneAlertChange: (Boolean) -> Unit,
    healthConnectState: HealthConnectSettingsState?,
    healthConnectBusy: Boolean,
    onHealthConnectSyncEnabledChange: (Boolean) -> Unit,
    onConnectHealthConnect: () -> Unit,
    onSyncHealthConnectNow: () -> Unit,
    onManageHealthConnect: () -> Unit,
    onInstallHealthConnect: () -> Unit,
    selfHostedState: SelfHostedSettingsState?,
    allowSelfHostedHttp: Boolean,
    selfHostedBusy: Boolean,
    selfHostedConnectionStatus: String?,
    onSaveSelfHostedConfiguration: (String, Boolean, String) -> Unit,
    onTestSelfHostedConnection: (String, Boolean, String) -> Unit,
    onSelfHostedSyncEnabledChange: (Boolean, String, Boolean, String) -> Unit,
    onSyncSelfHostedNow: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    var confirmingRestore by rememberSaveable { mutableStateOf(false) }
    var choiceSetting by rememberSaveable { mutableStateOf<ChoiceSetting?>(null) }
    var selfHostedServerAddress by rememberSaveable { mutableStateOf("") }
    var selfHostedUseHttps by rememberSaveable { mutableStateOf(true) }
    var selfHostedToken by rememberSaveable { mutableStateOf("") }
    var selfHostedConfigurationInitialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selfHostedState?.serverAddress, selfHostedState?.useHttps) {
        val state = selfHostedState
        if (!selfHostedConfigurationInitialized && state != null) {
            selfHostedServerAddress = state.serverAddress
            selfHostedUseHttps = state.useHttps
            selfHostedConfigurationInitialized = true
        }
    }
    val effectiveSelfHostedUseHttps = !allowSelfHostedHttp || selfHostedUseHttps
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = WorkoutSpacing.screen),
        verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.section),
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }
        item { SettingsSectionLabel("WORKOUT") }
        item {
            SettingsCard {
                ChoiceRow("Weight units", preferences.weightUnit.symbol) { choiceSetting = ChoiceSetting.WEIGHT_UNIT }
                ChoiceRow(
                    "Duration preparation",
                    if (preferences.durationPrepSeconds == 0) "Off" else "${preferences.durationPrepSeconds} seconds",
                ) { choiceSetting = ChoiceSetting.DURATION_PREP }
                SwitchRow("Keep phone screen awake", preferences.keepPhoneScreenAwake) {
                    onKeepPhoneScreenAwakeChange(it)
                }
            }
        }
        item { SettingsSectionLabel("APPEARANCE") }
        item {
            SettingsCard {
                ChoiceRow("Theme", preferences.themeMode.displayName) { choiceSetting = ChoiceSetting.THEME }
            }
        }
        item { SettingsSectionLabel("NOTIFICATIONS") }
        item {
            SettingsCard {
                SwitchRow("Rest completion alert", preferences.restCompletionPhoneAlert) {
                    onRestCompletionPhoneAlertChange(it)
                }
                SwitchRow("Duration completion alert", preferences.durationCompletionPhoneAlert) {
                    onDurationCompletionPhoneAlertChange(it)
                }
                TextButton(onClick = onOpenNotificationSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Android notification settings")
                }
            }
        }
        item { SettingsSectionLabel("HEALTH CONNECT") }
        item {
            SettingsCard {
                Column(
                    modifier = Modifier.padding(horizontal = WorkoutSpacing.card, vertical = WorkoutSpacing.item),
                    verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item),
                ) {
                    Text("Health Connect", style = MaterialTheme.typography.titleMedium)
                    Text(
                        healthConnectState.statusText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SwitchRow(
                    title = "Sync completed workouts",
                    checked = preferences.healthConnectSyncEnabled,
                    enabled = healthConnectState?.availability == HealthConnectAvailability.AVAILABLE && !healthConnectBusy,
                    onCheckedChange = onHealthConnectSyncEnabledChange,
                )
                when (healthConnectState?.availability) {
                    HealthConnectAvailability.AVAILABLE -> {
                        if (healthConnectState.hasWritePermission) {
                            TextButton(
                                onClick = onSyncHealthConnectNow,
                                enabled = !healthConnectBusy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (healthConnectBusy) "Syncing…" else "Sync now") }
                            TextButton(onClick = onManageHealthConnect, modifier = Modifier.fillMaxWidth()) {
                                Text("Manage Health Connect")
                            }
                        } else {
                            TextButton(
                                onClick = onConnectHealthConnect,
                                enabled = !healthConnectBusy,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Connect") }
                        }
                    }
                    HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED -> {
                        TextButton(onClick = onInstallHealthConnect, modifier = Modifier.fillMaxWidth()) {
                            Text("Install / update Health Connect")
                        }
                    }
                    HealthConnectAvailability.UNAVAILABLE,
                    null -> Unit
                }
            }
        }
        item { SettingsSectionLabel("SELF-HOSTED SYNC") }
        item {
            SettingsCard {
                Column(
                    modifier = Modifier.padding(WorkoutSpacing.card),
                    verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item),
                ) {
                    OutlinedTextField(
                        value = selfHostedServerAddress,
                        onValueChange = { value ->
                            val pastedFullUrl = value.trimStart().startsWith("http://", ignoreCase = true) ||
                                value.trimStart().startsWith("https://", ignoreCase = true)
                            val parsed = validateServerAddress(value, effectiveSelfHostedUseHttps)
                            if (pastedFullUrl && parsed is ServerAddressValidation.Valid) {
                                selfHostedServerAddress = parsed.serverAddress
                                selfHostedUseHttps = if (allowSelfHostedHttp) parsed.useHttps else true
                            } else {
                                selfHostedServerAddress = value
                            }
                        },
                        label = { Text("Server address") },
                        placeholder = { Text("192.168.1.140:5544") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (allowSelfHostedHttp) {
                        SwitchRow(
                            title = "Use HTTPS",
                            checked = selfHostedUseHttps,
                            enabled = !selfHostedBusy,
                            onCheckedChange = { selfHostedUseHttps = it },
                        )
                        if (!selfHostedUseHttps) {
                            Text(
                                "HTTP is intended for trusted local networks only. Your API token and workout data will not be encrypted.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    OutlinedTextField(
                        value = selfHostedToken,
                        onValueChange = { selfHostedToken = it },
                        label = { Text("API token") },
                        placeholder = { Text(if (selfHostedState?.hasToken == true) "Saved token" else "Enter token") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(WorkoutSpacing.item),
                    ) {
                        OutlinedButton(
                            onClick = {
                                onSaveSelfHostedConfiguration(
                                    selfHostedServerAddress,
                                    effectiveSelfHostedUseHttps,
                                    selfHostedToken,
                                )
                            },
                            enabled = !selfHostedBusy,
                            modifier = Modifier.weight(1f),
                        ) { Text("Save") }
                        OutlinedButton(
                            onClick = {
                                onTestSelfHostedConnection(
                                    selfHostedServerAddress,
                                    effectiveSelfHostedUseHttps,
                                    selfHostedToken,
                                )
                            },
                            enabled = !selfHostedBusy,
                            modifier = Modifier.weight(1f),
                        ) { Text("Test connection") }
                    }
                    Text(
                        "Last sync: ${formatSelfHostedLastSync(selfHostedState?.lastSuccessfulSyncAt)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    selfHostedConnectionStatus?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Pending uploads: ${selfHostedState?.pendingUploads ?: 0}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if ((selfHostedState?.permanentFailures ?: 0) > 0) {
                        Text(
                            "Needs attention: ${selfHostedState?.permanentFailures}",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    selfHostedState?.latestError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
                SwitchRow(
                    title = "Sync completed workouts",
                    checked = selfHostedState?.enabled == true,
                    enabled = !selfHostedBusy,
                ) {
                    onSelfHostedSyncEnabledChange(
                        it,
                        selfHostedServerAddress,
                        effectiveSelfHostedUseHttps,
                        selfHostedToken,
                    )
                }
                TextButton(
                    onClick = onSyncSelfHostedNow,
                    enabled = selfHostedState?.enabled == true && !selfHostedBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (selfHostedBusy) "Syncing…" else "Sync now") }
            }
        }
        item { SettingsSectionLabel("DATA") }
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(WorkoutSpacing.card), verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item)) {
                    Text("Backup & restore", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Export or restore a local JSON backup of programs, history, and active workout state.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) { Text("Export backup") }
                    OutlinedButton(onClick = { confirmingRestore = true }, modifier = Modifier.fillMaxWidth()) { Text("Restore backup") }
                }
            }
        }
        item { SettingsSectionLabel("ABOUT") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(WorkoutSpacing.card), verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item)) {
                    Text("Workout Companion", style = MaterialTheme.typography.titleMedium)
                    versionName?.let { Text("Version $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }

    choiceSetting?.let { setting ->
        val title: String
        val choices: List<Pair<String, () -> Unit>>
        when (setting) {
            ChoiceSetting.WEIGHT_UNIT -> {
                title = "Weight units"
                choices = WeightUnit.entries.map { unit ->
                    unit.symbol to { onWeightUnitChange(unit) }
                }
            }
            ChoiceSetting.THEME -> {
                title = "Theme"
                choices = ThemeMode.entries.map { mode ->
                    mode.displayName to { onThemeModeChange(mode) }
                }
            }
            ChoiceSetting.DURATION_PREP -> {
                title = "Duration preparation"
                choices = listOf(0, 3, 5).map { seconds ->
                    (if (seconds == 0) "Off" else "$seconds seconds") to {
                        onDurationPrepSecondsChange(seconds)
                    }
                }
            }
        }
        AlertDialog(
            onDismissRequest = { choiceSetting = null },
            title = { Text(title) },
            text = {
                Column {
                    choices.forEach { (label, select) ->
                        TextButton(
                            onClick = { select(); choiceSetting = null },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choiceSetting = null }) { Text("Cancel") } },
        )
    }
    if (confirmingRestore) {
        AlertDialog(
            onDismissRequest = { confirmingRestore = false },
            title = { Text("Restore backup?") },
            text = { Text("This replaces the local Workout Companion data on this device with the selected backup file.") },
            confirmButton = {
                Button(onClick = { confirmingRestore = false; onRestoreBackup() }) { Text("Choose backup") }
            },
            dismissButton = { TextButton(onClick = { confirmingRestore = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun ChoiceRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(WorkoutSpacing.card),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(WorkoutSpacing.card),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

private val ThemeMode.displayName: String
    get() = name.lowercase().replaceFirstChar(Char::uppercase)

private val HealthConnectSettingsState?.statusText: String
    get() = when {
        this == null -> "Checking availability…"
        availability == HealthConnectAvailability.PROVIDER_UPDATE_REQUIRED -> "Update required"
        availability == HealthConnectAvailability.UNAVAILABLE -> "Not available on this device"
        hasWritePermission -> "Connected"
        else -> "Permission required"
    }
