package com.jupiman.workouttracker.data.repository

import com.jupiman.workouttracker.preferences.WeightUnit
import java.math.BigDecimal
import java.math.RoundingMode

private val KilogramsToPounds = BigDecimal("2.2046226218")

fun parseWeight(text: String, unit: WeightUnit): Int {
    val normalized = text.trim().replace(',', '.')
    require(normalized.isNotEmpty()) { "Weight cannot be empty." }

    val enteredValue = normalized.toBigDecimalOrNull()
        ?: throw IllegalArgumentException("Weight must be a number.")
    require(enteredValue >= BigDecimal.ZERO) { "Weight cannot be negative." }
    val kilograms = when (unit) {
        WeightUnit.KG -> enteredValue
        WeightUnit.LB -> enteredValue.divide(KilogramsToPounds, 12, RoundingMode.HALF_UP)
    }

    return kilograms
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
        .intValueExact()
}

fun formatWeightValue(centiKg: Int, unit: WeightUnit): String = when (unit) {
    WeightUnit.KG -> BigDecimal(centiKg).movePointLeft(2)
    WeightUnit.LB -> BigDecimal(centiKg).movePointLeft(2).multiply(KilogramsToPounds)
        .setScale(2, RoundingMode.HALF_UP)
}.stripTrailingZeros().toPlainString()

fun formatWeight(centiKg: Int, unit: WeightUnit): String =
    "${formatWeightValue(centiKg, unit)} ${unit.symbol}"

fun parseCentiKg(text: String): Int = parseWeight(text, WeightUnit.KG)

fun formatCentiKg(centiKg: Int): String = formatWeightValue(centiKg, WeightUnit.KG)
