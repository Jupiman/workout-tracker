package com.jupiman.workouttracker.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class WorkoutIcon {
    Workout,
    Program,
    History,
    Back,
    Menu,
    Add,
}

@Composable
fun WorkoutGlyph(
    icon: WorkoutIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    color: Color = androidx.compose.material3.LocalContentColor.current,
) {
    Canvas(
        modifier = modifier
            .size(24.dp)
            .then(
                if (contentDescription == null) Modifier else Modifier.semantics {
                    this.contentDescription = contentDescription
                },
            ),
    ) {
        val stroke = 2.dp.toPx()
        val round = StrokeCap.Round
        when (icon) {
            WorkoutIcon.Workout -> {
                drawLine(color, Offset(size.width * .25f, size.height * .5f), Offset(size.width * .75f, size.height * .5f), stroke, round)
                drawLine(color, Offset(size.width * .25f, size.height * .32f), Offset(size.width * .25f, size.height * .68f), stroke * 1.45f, round)
                drawLine(color, Offset(size.width * .15f, size.height * .38f), Offset(size.width * .15f, size.height * .62f), stroke * 1.45f, round)
                drawLine(color, Offset(size.width * .75f, size.height * .32f), Offset(size.width * .75f, size.height * .68f), stroke * 1.45f, round)
                drawLine(color, Offset(size.width * .85f, size.height * .38f), Offset(size.width * .85f, size.height * .62f), stroke * 1.45f, round)
            }
            WorkoutIcon.Program -> {
                listOf(.28f, .5f, .72f).forEach { y ->
                    drawCircle(color, radius = stroke * .7f, center = Offset(size.width * .2f, size.height * y))
                    drawLine(color, Offset(size.width * .34f, size.height * y), Offset(size.width * .82f, size.height * y), stroke, round)
                }
            }
            WorkoutIcon.History -> {
                drawCircle(color, radius = size.minDimension * .34f, center = center, style = Stroke(stroke))
                drawLine(color, center, Offset(center.x, size.height * .3f), stroke, round)
                drawLine(color, center, Offset(size.width * .66f, size.height * .57f), stroke, round)
            }
            WorkoutIcon.Back -> {
                drawLine(color, Offset(size.width * .7f, size.height * .2f), Offset(size.width * .35f, size.height * .5f), stroke, round)
                drawLine(color, Offset(size.width * .35f, size.height * .5f), Offset(size.width * .7f, size.height * .8f), stroke, round)
            }
            WorkoutIcon.Menu -> listOf(.28f, .5f, .72f).forEach { y ->
                drawCircle(color, radius = stroke * .75f, center = Offset(size.width * .5f, size.height * y))
            }
            WorkoutIcon.Add -> {
                drawLine(color, Offset(size.width * .25f, size.height * .5f), Offset(size.width * .75f, size.height * .5f), stroke, round)
                drawLine(color, Offset(size.width * .5f, size.height * .25f), Offset(size.width * .5f, size.height * .75f), stroke, round)
            }
        }
    }
}
