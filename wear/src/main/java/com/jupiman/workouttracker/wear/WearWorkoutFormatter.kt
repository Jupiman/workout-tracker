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
    if (remainingMillis <= 0L) return "READY"
    val totalSeconds = max(1L, (remainingMillis + 999L) / 1_000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "Rest %d:%02d".format(minutes, seconds)
}
