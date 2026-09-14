package com.jupiman.workouttracker.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

object WorkoutSpacing {
    val screen = 20.dp
    val card = 16.dp
    val compactCard = 10.dp
    val item = 10.dp
    val section = 20.dp
}

object WorkoutRadii {
    val card = 18.dp
    val row = 14.dp
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
        WorkoutVisualState.Completed -> if (colors.background.luminance() < 0.5f) {
            WorkoutStateColors(
                container = Color(0xFF173524),
                content = Color(0xFFD7F8E1),
                border = Color(0xFF69C88D),
            )
        } else {
            WorkoutStateColors(
                container = Color(0xFFDDF5E5),
                content = Color(0xFF173B24),
                border = Color(0xFF3E8057),
            )
        }
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
fun WorkoutScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    supportingText: String? = null,
    navigation: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigation?.invoke()
        Column(
            modifier = Modifier.weight(1f),
        ) {
            eyebrow?.let {
                Text(
                    text = it.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            supportingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun WorkoutEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)),
        shape = RoundedCornerShape(WorkoutRadii.card),
    ) {
        Column(
            modifier = Modifier.padding(WorkoutSpacing.card),
        ) {
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(WorkoutRadii.row))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), RoundedCornerShape(WorkoutRadii.row))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text("WORKOUT COMPANION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun WorkoutInlineError(
    message: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(WorkoutRadii.row),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
