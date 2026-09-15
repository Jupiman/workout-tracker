package com.jupiman.workouttracker.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jupiman.workouttracker.data.repository.formatWeightValue
import com.jupiman.workouttracker.data.repository.parseWeight
import com.jupiman.workouttracker.preferences.WeightUnit
import com.jupiman.workouttracker.ui.theme.WorkoutSpacing
import kotlin.math.roundToInt

internal const val DefaultWeightSliderUpperBoundCentiKg = 15_000

internal val WeightSliderUpperBoundBucketsCentiKg = listOf(10_000, 15_000, 20_000, 30_000, 50_000)

@Composable
internal fun WeightAdjuster(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    incrementCentiKg: Int,
    modifier: Modifier = Modifier,
    weightUnit: WeightUnit = WeightUnit.KG,
) {
    val normalizedIncrement = incrementCentiKg.coerceAtLeast(1)
    val parsedWeight = value.toCentiKgOrNull(weightUnit)
    var sliderDragInProgress by remember { mutableStateOf(false) }
    var sliderUpperBoundCentiKg by remember { mutableStateOf(DefaultWeightSliderUpperBoundCentiKg) }

    LaunchedEffect(parsedWeight, sliderDragInProgress) {
        if (!sliderDragInProgress && parsedWeight != null && parsedWeight > sliderUpperBoundCentiKg) {
            sliderUpperBoundCentiKg = upperBoundBucketFor(
                weightCentiKg = parsedWeight,
                currentUpperBoundCentiKg = sliderUpperBoundCentiKg,
            )
        }
    }

    val sliderWeight = (parsedWeight ?: 0)
        .coerceIn(0, sliderUpperBoundCentiKg)
        .toFloat() / 100f

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(WorkoutSpacing.item),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { nextValue ->
                onValueChange(nextValue)
                nextValue.toCentiKgOrNull(weightUnit)?.let { typedWeight ->
                    if (!sliderDragInProgress && typedWeight > sliderUpperBoundCentiKg) {
                        sliderUpperBoundCentiKg = upperBoundBucketFor(
                            weightCentiKg = typedWeight,
                            currentUpperBoundCentiKg = sliderUpperBoundCentiKg,
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WorkoutSpacing.item),
        ) {
            OutlinedButton(
                onClick = {
                    val nextValue = value.adjustedBy(-normalizedIncrement, weightUnit)
                    onValueChange(nextValue)
                },
            ) {
                Text("-")
            }
            Slider(
                value = sliderWeight,
                onValueChange = { rawKg ->
                    sliderDragInProgress = true
                    onValueChange(
                        formatWeightValue(
                            rawKg
                                .centiKgFromSlider(normalizedIncrement)
                                .coerceIn(0, sliderUpperBoundCentiKg),
                            weightUnit,
                        ),
                    )
                },
                onValueChangeFinished = {
                    sliderDragInProgress = false
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp),
                enabled = parsedWeight != null,
                valueRange = 0f..(sliderUpperBoundCentiKg / 100f),
            )
            OutlinedButton(
                onClick = {
                    val nextValue = value.adjustedBy(normalizedIncrement, weightUnit)
                    onValueChange(nextValue)
                    nextValue.toCentiKgOrNull(weightUnit)?.let { adjustedWeight ->
                        if (adjustedWeight > sliderUpperBoundCentiKg) {
                            sliderUpperBoundCentiKg = upperBoundBucketFor(
                                weightCentiKg = adjustedWeight,
                                currentUpperBoundCentiKg = sliderUpperBoundCentiKg,
                            )
                        }
                    }
                },
            ) {
                Text("+")
            }
        }
    }
}

internal fun String.toPositiveCentiKgOrDefault(
    defaultCentiKg: Int,
    weightUnit: WeightUnit = WeightUnit.KG,
): Int =
    toCentiKgOrNull(weightUnit)
        ?.coerceAtLeast(1)
        ?: defaultCentiKg

internal fun upperBoundBucketFor(
    weightCentiKg: Int,
    currentUpperBoundCentiKg: Int = DefaultWeightSliderUpperBoundCentiKg,
): Int {
    val minimumUpperBound = maxOf(currentUpperBoundCentiKg, DefaultWeightSliderUpperBoundCentiKg)
    val requiredUpperBound = maxOf(weightCentiKg, minimumUpperBound)
    return WeightSliderUpperBoundBucketsCentiKg.firstOrNull { bucket ->
        bucket >= requiredUpperBound
    } ?: WeightSliderUpperBoundBucketsCentiKg.last()
}

private fun String.adjustedBy(deltaCentiKg: Int, weightUnit: WeightUnit): String {
    val current = toCentiKgOrNull(weightUnit) ?: 0
    return formatWeightValue((current + deltaCentiKg).coerceAtLeast(0), weightUnit)
}

private fun Float.centiKgFromSlider(incrementCentiKg: Int): Int {
    val rawCentiKg = (this * 100f).roundToInt()
    return (rawCentiKg.toFloat() / incrementCentiKg)
        .roundToInt() * incrementCentiKg
}

private fun String.toCentiKgOrNull(weightUnit: WeightUnit): Int? =
    runCatching { parseWeight(this, weightUnit) }.getOrNull()
