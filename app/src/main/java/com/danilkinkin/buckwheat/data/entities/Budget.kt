package com.danilkinkin.buckwheat.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

const val DEFAULT_BUDGET_ID = 1

enum class BudgetPeriod {
    // Classic Buckwheat budget: an amount until a chosen finish date
    ONE_OFF,
    WEEKLY,
    // Starts on the day the budget is set up
    MONTHLY,
    // From the first to the last day of a calendar month
    CALENDAR_MONTH,
    EVERY_N_DAYS,
}

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "uid")
    val uid: Int = 0,

    @ColumnInfo(name = "name")
    val name: String = "",

    @ColumnInfo(name = "emoji")
    val emoji: String = "",

    @ColumnInfo(name = "period")
    val period: BudgetPeriod = BudgetPeriod.ONE_OFF,

    @ColumnInfo(name = "periodDays")
    val periodDays: Int = 7,

    // Amount per period for recurring budgets
    @ColumnInfo(name = "amount")
    val amount: BigDecimal = BigDecimal.ZERO,

    @ColumnInfo(name = "carryOver")
    val carryOver: Boolean = false,
)

val Budget.isRecurring: Boolean
    get() = period != BudgetPeriod.ONE_OFF
