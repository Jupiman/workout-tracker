package com.jupiman.workouttracker.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object WorkoutSpacing {
    val screen = 16.dp
    val card = 14.dp
    val compactCard = 10.dp
    val item = 8.dp
    val section = 16.dp
}

object WorkoutRadii {
    val card = 12.dp
    val row = 10.dp
    val pill = 999.dp
}

enum class WorkoutVisualState {
    Current,
    Pending,
    Completed,
    Skipped,
    Rest,
    Ready,
    Disabled,
}

@Immutable
data class WorkoutStateColors(
    val container: Color,
    val content: Color,
    val border: Color,
)

@Composable
fun workoutStateColors(state: WorkoutVisualState): WorkoutStateColors {
    val colors = MaterialTheme.colorScheme
    return when (state) {
        WorkoutVisualState.Current -> WorkoutStateColors(
            container = colors.primaryContainer,
            content = colors.onPrimaryContainer,
            border = colors.primary,
        )
        WorkoutVisualState.Pending -> WorkoutStateColors(
            container = colors.surfaceVariant.copy(alpha = 0.78f),
            content = colors.onSurfaceVariant,
            border = colors.outline.copy(alpha = 0.24f),
        )
        WorkoutVisualState.Completed -> WorkoutStateColors(
            container = Color(0xFF173524),
            content = Color(0xFFD7F8E1),
            border = Color(0xFF69C88D),
        )
        WorkoutVisualState.Skipped -> WorkoutStateColors(
            container = colors.errorContainer.copy(alpha = 0.8f),
            content = colors.onErrorContainer,
            border = colors.error,
        )
        WorkoutVisualState.Rest -> WorkoutStateColors(
            container = colors.tertiaryContainer.copy(alpha = 0.72f),
            content = colors.onTertiaryContainer,
            border = colors.tertiary,
        )
        WorkoutVisualState.Ready -> WorkoutStateColors(
            container = colors.primaryContainer,
            content = colors.onPrimaryContainer,
            border = colors.primary,
        )
        WorkoutVisualState.Disabled -> WorkoutStateColors(
            container = colors.surfaceVariant.copy(alpha = 0.42f),
            content = colors.onSurfaceVariant.copy(alpha = 0.62f),
            border = colors.outline.copy(alpha = 0.16f),
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    state: WorkoutVisualState,
    modifier: Modifier = Modifier,
) {
    val palette = workoutStateColors(state)
    Box(
        modifier = modifier
            .background(palette.container, RoundedCornerShape(WorkoutRadii.pill))
            .padding(PaddingValues(horizontal = 10.dp, vertical = 5.dp)),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = palette.content,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
