package com.jupiman.workouttracker.wear

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
