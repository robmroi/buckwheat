package com.danilkinkin.buckwheat.util

import com.danilkinkin.buckwheat.data.entities.BudgetPeriod
import java.time.LocalDate

// Returns the first and last day of a recurring period that starts at `from`.
// Periods are anchored on the day the budget was set up, so the first period
// is always full length (e.g. a weekly budget set up on Thursday runs Thu-Wed).
fun periodBounds(period: BudgetPeriod, periodDays: Int, from: LocalDate): Pair<LocalDate, LocalDate> {
    val finish = when (period) {
        BudgetPeriod.WEEKLY -> from.plusDays(6)
        BudgetPeriod.MONTHLY -> from.plusMonths(1).minusDays(1)
        BudgetPeriod.EVERY_N_DAYS -> from.plusDays(periodDays.coerceAtLeast(1).toLong() - 1)
        BudgetPeriod.ONE_OFF -> from
    }

    return from to finish
}
