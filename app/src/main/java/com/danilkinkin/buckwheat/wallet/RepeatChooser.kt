package com.danilkinkin.buckwheat.wallet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danilkinkin.buckwheat.LocalWindowInsets
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.base.CheckedRow
import com.danilkinkin.buckwheat.base.LocalBottomSheetScrollState
import com.danilkinkin.buckwheat.data.entities.Budget
import com.danilkinkin.buckwheat.data.entities.BudgetPeriod

const val REPEAT_CHOOSER_SHEET = "repeatChooser"

enum class RepeatOption(val period: BudgetPeriod, val periodDays: Int) {
    NEVER(BudgetPeriod.ONE_OFF, 0),
    WEEKLY(BudgetPeriod.WEEKLY, 7),
    TWO_WEEKS(BudgetPeriod.EVERY_N_DAYS, 14),
    CALENDAR_MONTH(BudgetPeriod.CALENDAR_MONTH, 0),
    MONTHLY(BudgetPeriod.MONTHLY, 0);

    val isRecurring: Boolean
        get() = period != BudgetPeriod.ONE_OFF

    companion object {
        fun of(budget: Budget): RepeatOption = when (budget.period) {
            BudgetPeriod.ONE_OFF -> NEVER
            BudgetPeriod.WEEKLY -> WEEKLY
            BudgetPeriod.MONTHLY -> MONTHLY
            BudgetPeriod.CALENDAR_MONTH -> CALENDAR_MONTH
            BudgetPeriod.EVERY_N_DAYS -> TWO_WEEKS
        }
    }
}

@Composable
fun RepeatOption.label() = stringResource(
    when (this) {
        RepeatOption.NEVER -> R.string.repeat_never
        RepeatOption.WEEKLY -> R.string.repeat_weekly
        RepeatOption.TWO_WEEKS -> R.string.repeat_two_weeks
        RepeatOption.CALENDAR_MONTH -> R.string.repeat_calendar_month
        RepeatOption.MONTHLY -> R.string.repeat_monthly
    }
)

@Composable
fun RepeatChooser(
    selected: RepeatOption,
    onSelect: (RepeatOption) -> Unit,
) {
    val localBottomSheetScrollState = LocalBottomSheetScrollState.current
    val navigationBarHeight = LocalWindowInsets.current.calculateBottomPadding()
        .coerceAtLeast(16.dp)

    Surface(Modifier.padding(top = localBottomSheetScrollState.topPadding)) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.repeat_label),
                    style = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                )
            }
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = navigationBarHeight)
            ) {
                RepeatOption.entries.forEach { option ->
                    CheckedRow(
                        checked = option == selected,
                        onValueChange = { onSelect(option) },
                        text = option.label(),
                    )
                }
            }
        }
    }
}
