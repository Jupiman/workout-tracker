package com.jupiman.workouttracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Lavender = Color(0xFFC9B6FF)
private val LavenderDeep = Color(0xFF7758D8)
private val Ink = Color(0xFF101116)
private val InkRaised = Color(0xFF181A22)
private val InkSoft = Color(0xFF222531)
private val Mist = Color(0xFFE8E5EF)
private val Muted = Color(0xFFBDB7CC)
private val Warning = Color(0xFFFFD166)

private val LightColors = lightColorScheme(
    primary = LavenderDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF21123D),
    secondary = Color(0xFF665A7F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF21192E),
    tertiary = Color(0xFF7A5D00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE59A),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFFBF9FF),
    onBackground = Color(0xFF17151D),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF17151D),
    surfaceVariant = Color(0xFFE8E3EE),
    onSurfaceVariant = Color(0xFF494453),
    outline = Color(0xFF7A7385),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Lavender,
    onPrimary = Color(0xFF2B165F),
    primaryContainer = Color(0xFF4C318F),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFD0C2E8),
    onSecondary = Color(0xFF352D43),
    secondaryContainer = Color(0xFF4B435A),
    onSecondaryContainer = Color(0xFFEDE4FF),
    tertiary = Warning,
    onTertiary = Color(0xFF3B2D00),
    tertiaryContainer = Color(0xFF5A4300),
    onTertiaryContainer = Color(0xFFFFE59A),
    background = Ink,
    onBackground = Mist,
    surface = InkRaised,
    onSurface = Mist,
    surfaceVariant = InkSoft,
    onSurfaceVariant = Muted,
    outline = Color(0xFF8F879B),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val WorkoutShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

private val WorkoutTypography = Typography(
    headlineMedium = Typography().headlineMedium.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    headlineSmall = Typography().headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp,
    ),
    titleLarge = Typography().titleLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleMedium = Typography().titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    labelLarge = Typography().labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
)

@Composable
fun WorkoutTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WorkoutTypography,
        shapes = WorkoutShapes,
        content = content,
    )
}
