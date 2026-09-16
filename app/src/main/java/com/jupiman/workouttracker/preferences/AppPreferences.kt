package com.jupiman.workouttracker.preferences

enum class WeightUnit(val symbol: String) {
    KG("kg"),
    LB("lb"),
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppPreferences(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val durationPrepSeconds: Int = 3,
    val keepPhoneScreenAwake: Boolean = false,
    val restCompletionPhoneAlert: Boolean = true,
    val durationCompletionPhoneAlert: Boolean = true,
    val healthConnectSyncEnabled: Boolean = false,
    val selfHostedSyncEnabled: Boolean = false,
    val selfHostedServerAddress: String = "",
    val selfHostedUseHttps: Boolean = true,
    val selfHostedLastSuccessfulSyncAt: Long? = null,
    val selfHostedLastError: String? = null,
)

fun shouldUseDarkTheme(themeMode: ThemeMode, systemDark: Boolean): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

fun shouldKeepPhoneScreenAwake(preferences: AppPreferences, hasActiveWorkout: Boolean): Boolean =
    preferences.keepPhoneScreenAwake && hasActiveWorkout

fun interface DurationPreparationProvider {
    suspend fun durationPrepSeconds(): Int
}

object DefaultDurationPreparationProvider : DurationPreparationProvider {
    override suspend fun durationPrepSeconds(): Int = 3
}
