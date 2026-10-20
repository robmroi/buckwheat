package com.danilkinkin.buckwheat.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.di.PeriodSummary
import com.danilkinkin.buckwheat.ui.colorBad
import com.danilkinkin.buckwheat.ui.colorGood
import com.danilkinkin.buckwheat.util.harmonize
import com.danilkinkin.buckwheat.util.numberFormat
import com.danilkinkin.buckwheat.util.prettyDate
import com.danilkinkin.buckwheat.util.toPalette
import java.math.BigDecimal

@Composable
fun PastPeriodsCard(
    modifier: Modifier = Modifier,
    periods: List<PeriodSummary>,
    currency: ExtendCurrency,
) {
    val context = LocalContext.current
    val goodColor = toPalette(harmonize(colorGood)).main
    val badColor = toPalette(harmonize(colorBad)).main

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.past_periods_title),
                style = MaterialTheme.typography.labelMedium,
            )
            periods.take(12).forEach { period ->
                val isOver = period.spent > period.budget
                val progress = if (period.budget > BigDecimal.ZERO) {
                    (period.spent.toFloat() / period.budget.toFloat()).coerceIn(0f, 1f)
                } else {
                    1f
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${
                            prettyDate(period.startDate, pattern = "dd MMM", simplifyIfToday = false)
                        } – ${
                            prettyDate(period.finishDate, pattern = "dd MMM", simplifyIfToday = false)
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(
                            R.string.spent_of,
                            numberFormat(context, period.spent, currency = currency),
                            numberFormat(context, period.budget, currency = currency),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isOver) badColor else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (isOver) badColor else goodColor,
                )
            }
        }
    }
}
