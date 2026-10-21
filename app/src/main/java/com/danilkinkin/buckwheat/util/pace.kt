package com.danilkinkin.buckwheat.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Date

data class Pace(
    // How much was allowed to spend until the end of today if spending linearly
    val allowed: BigDecimal,
    val spent: BigDecimal,
) {
    // Positive when under pace, negative when over pace
    val difference: BigDecimal
        get() = allowed - spent
}

fun calculatePace(
    budget: BigDecimal,
    startDate: Date,
    finishDate: Date,
    spent: BigDecimal,
    today: Date = Date(),
): Pace {
    val totalDays = countDays(finishDate, startDate).coerceAtLeast(1)
    val elapsedDays = countDays(today, startDate).coerceIn(1, totalDays)

    val allowed = budget
        .multiply(elapsedDays.toBigDecimal())
        .divide(totalDays.toBigDecimal(), 2, RoundingMode.HALF_EVEN)

    return Pace(allowed = allowed, spent = spent)
}
