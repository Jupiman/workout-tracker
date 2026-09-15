package com.jupiman.workouttracker.wear

import com.jupiman.workouttracker.wearprotocol.WearTrackingMode
import com.jupiman.workouttracker.wearprotocol.WorkoutWearState
import com.jupiman.workouttracker.wearprotocol.WearWeightUnit
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max

fun formatCentiKg(centiKg: Int): String {
    val whole = centiKg / 100
    val fraction = centiKg % 100
    return if (fraction == 0) {
        whole.toString()
    } else {
        val tenths = fraction / 10
        if (fraction % 10 == 0) "$whole.$tenths" else "$whole.${fraction.toString().padStart(2, '0')}"
    }
}

private val KilogramsToPounds = BigDecimal("2.2046226218")

fun formatWeight(centiKg: Int, unit: WearWeightUnit): String = when (unit) {
    WearWeightUnit.KG -> formatCentiKg(centiKg)
    WearWeightUnit.LB -> BigDecimal(centiKg).movePointLeft(2).multiply(KilogramsToPounds)
        .setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}

fun targetText(state: WorkoutWearState): String = when (state.trackingMode) {
    WearTrackingMode.WEIGHT_REPS -> "${formatWeight(state.weightCentiKg ?: 0, state.weightUnit)}${state.weightUnit.name.lowercase()} × ${state.targetReps ?: 0}"
    WearTrackingMode.REPS -> "${state.targetReps ?: 0} reps"
    WearTrackingMode.DURATION -> "${state.targetDurationSeconds ?: 0} sec"
}

fun targetFontSizeSp(text: String): Int = when {
    text.length >= 13 -> 18
    text.length >= 10 -> 20
    else -> 25
}

fun restText(
    restEndsAt: Long?,
    now: Long,
): String? {
    val endsAt = restEndsAt ?: return null
    val remainingMillis = endsAt - now
    val totalSeconds = if (remainingMillis > 0L) {
        max(1L, (remainingMillis + 999L) / 1_000L)
    } else {
        -(((-remainingMillis) + 999L) / 1_000L)
    }
    val sign = if (remainingMillis <= 0L) "-" else ""
    val absoluteSeconds = kotlin.math.abs(totalSeconds)
    val minutes = absoluteSeconds / 60L
    val seconds = absoluteSeconds % 60L
    return "Rest $sign%d:%02d".format(minutes, seconds)
}

fun isRestOverdue(
    restEndsAt: Long?,
    now: Long,
): Boolean {
    val endsAt = restEndsAt ?: return false
    return now >= endsAt
}
