package com.danilkinkin.buckwheat.util

import com.danilkinkin.buckwheat.data.entities.BudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class PeriodsTest {
    @Test
    fun weeklyPeriodLastsSevenDays() {
        val start = LocalDate.of(2026, 10, 22)

        assertEquals(
            start to LocalDate.of(2026, 10, 28),
            periodBounds(BudgetPeriod.WEEKLY, 7, start),
        )
    }

    @Test
    fun monthlyPeriodEndsTheDayBeforeSameDayNextMonth() {
        assertEquals(
            LocalDate.of(2026, 10, 20) to LocalDate.of(2026, 11, 19),
            periodBounds(BudgetPeriod.MONTHLY, 0, LocalDate.of(2026, 10, 20)),
        )
        assertEquals(
            LocalDate.of(2026, 1, 1) to LocalDate.of(2026, 1, 31),
            periodBounds(BudgetPeriod.MONTHLY, 0, LocalDate.of(2026, 1, 1)),
        )
    }

    @Test
    fun calendarMonthCoversTheWholeMonthEvenWhenSetUpMidMonth() {
        assertEquals(
            LocalDate.of(2026, 10, 1) to LocalDate.of(2026, 10, 31),
            periodBounds(BudgetPeriod.CALENDAR_MONTH, 0, LocalDate.of(2026, 10, 21)),
        )
        assertEquals(
            LocalDate.of(2028, 2, 1) to LocalDate.of(2028, 2, 29),
            periodBounds(BudgetPeriod.CALENDAR_MONTH, 0, LocalDate.of(2028, 2, 29)),
        )
    }

    @Test
    fun calendarMonthFollowedByTheNextOne() {
        val (_, finish) = periodBounds(BudgetPeriod.CALENDAR_MONTH, 0, LocalDate.of(2026, 12, 15))

        assertEquals(
            LocalDate.of(2027, 1, 1) to LocalDate.of(2027, 1, 31),
            periodBounds(BudgetPeriod.CALENDAR_MONTH, 0, finish.plusDays(1)),
        )
    }

    @Test
    fun everyNDaysPeriod() {
        val start = LocalDate.of(2026, 10, 20)

        assertEquals(
            start to LocalDate.of(2026, 11, 2),
            periodBounds(BudgetPeriod.EVERY_N_DAYS, 14, start),
        )
    }

    @Test
    fun paceIsLinear() {
        val start = LocalDate.of(2026, 10, 1)
        val finish = LocalDate.of(2026, 10, 10)

        // Day 3 of 10: 30 of 100 allowed
        val pace = calculatePace(
            budget = BigDecimal(100),
            startDate = start.toDate(),
            finishDate = finish.toDate(),
            spent = BigDecimal(40),
            today = LocalDate.of(2026, 10, 3).toDate(),
        )

        assertEquals(BigDecimal("30.00"), pace.allowed)
        assertEquals(BigDecimal("-10.00"), pace.difference)
    }

    @Test
    fun paceIsCappedAtTheWholeBudgetAfterTheEnd() {
        val pace = calculatePace(
            budget = BigDecimal(70),
            startDate = LocalDate.of(2026, 10, 1).toDate(),
            finishDate = LocalDate.of(2026, 10, 7).toDate(),
            spent = BigDecimal(10),
            today = LocalDate.of(2026, 12, 1).toDate(),
        )

        assertEquals(BigDecimal("70.00"), pace.allowed)
    }
}
