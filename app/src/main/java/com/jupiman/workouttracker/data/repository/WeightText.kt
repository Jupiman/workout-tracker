package com.jupiman.workouttracker.data.repository

import java.math.BigDecimal
import java.math.RoundingMode

fun parseCentiKg(text: String): Int {
    val normalized = text.trim().replace(',', '.')
    require(normalized.isNotEmpty()) { "Weight cannot be empty." }

    val kilograms = normalized.toBigDecimalOrNull()
        ?: throw IllegalArgumentException("Weight must be a number.")
    require(kilograms >= BigDecimal.ZERO) { "Weight cannot be negative." }

    return kilograms
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
        .intValueExact()
}

fun formatCentiKg(centiKg: Int): String {
    val kilograms = BigDecimal(centiKg).movePointLeft(2).stripTrailingZeros()
    return kilograms.toPlainString()
}

