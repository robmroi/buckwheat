package com.danilkinkin.buckwheat.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.danilkinkin.buckwheat.R
import com.danilkinkin.buckwheat.data.ExtendCurrency
import com.danilkinkin.buckwheat.data.entities.Transaction
import com.danilkinkin.buckwheat.data.entities.TransactionType
import com.danilkinkin.buckwheat.ui.BuckwheatTheme
import com.danilkinkin.buckwheat.ui.colorBad
import com.danilkinkin.buckwheat.ui.colorGood
import com.danilkinkin.buckwheat.util.calculatePace
import com.danilkinkin.buckwheat.util.countDays
import com.danilkinkin.buckwheat.util.harmonize
import com.danilkinkin.buckwheat.util.numberFormat
import com.danilkinkin.buckwheat.util.prettyDate
import com.danilkinkin.buckwheat.util.toDate
import com.danilkinkin.buckwheat.util.toPalette
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Date

// Cumulative spending of the period compared with a linear "allowed" line
@Composable
fun PaceChartCard(
    modifier: Modifier = Modifier,
    budget: BigDecimal,
    spends: List<Transaction>,
    startDate: Date,
    finishDate: Date,
    currency: ExtendCurrency,
    today: Date = Date(),
) {
    val context = LocalContext.current

    val totalDays = countDays(finishDate, startDate).coerceAtLeast(1)
    val elapsedDays = countDays(today, startDate).coerceIn(1, totalDays)

    // Spent until the end of every elapsed day
    val cumulative = FloatArray(elapsedDays)
    var sum = BigDecimal.ZERO
    val sortedSpends = spends.sortedBy { it.date.time }
    var index = 0
    for (day in 0 until elapsedDays) {
        while (
            index < sortedSpends.size
            && countDays(sortedSpends[index].date, startDate) - 1 <= day
        ) {
            sum += sortedSpends[index].value
            index += 1
        }
        cumulative[day] = sum.toFloat()
    }

    val spentTotal = sortedSpends.fold(BigDecimal.ZERO) { acc, spent -> acc + spent.value }
    val pace = calculatePace(budget, startDate, finishDate, spentTotal, today)
    val isOver = pace.difference < BigDecimal.ZERO

    val goodColor = toPalette(harmonize(colorGood)).main
    val badColor = toPalette(harmonize(colorBad)).main
    val spentColor = if (isOver) badColor else goodColor
    val allowedColor = LocalContentColor.current.copy(alpha = 0.4f)

    val maxValue = maxOf(budget.toFloat(), spentTotal.toFloat(), 1f) * 1.05f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(vertical = 16.dp, horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.pace_title),
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    pace.difference.signum() == 0 -> stringResource(R.string.pace_on_track)
                    isOver -> stringResource(
                        R.string.pace_over,
                        numberFormat(context, pace.difference.abs(), currency = currency),
                    )
                    else -> stringResource(
                        R.string.pace_under,
                        numberFormat(context, pace.difference, currency = currency),
                    )
                },
                style = MaterialTheme.typography.titleLarge,
                color = spentColor,
            )
            Spacer(Modifier.height(16.dp))
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val stepX = size.width / totalDays
                fun y(value: Float) = size.height - value / maxValue * size.height

                // Allowed: straight line from nothing to the whole budget
                drawLine(
                    color = allowedColor,
                    start = Offset(0f, y(0f)),
                    end = Offset(size.width, y(budget.toFloat())),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 6.dp.toPx())
                    ),
                )

                val line = Path().apply {
                    moveTo(0f, y(0f))
                    cumulative.forEachIndexed { day, value ->
                        lineTo(stepX * (day + 1), y(value))
                    }
                }
                val area = Path().apply {
                    addPath(line)
                    lineTo(stepX * elapsedDays, size.height)
                    lineTo(0f, size.height)
                    close()
                }

                drawPath(area, color = spentColor.copy(alpha = 0.15f))
                drawPath(
                    line,
                    color = spentColor,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                drawCircle(
                    color = spentColor,
                    radius = 4.dp.toPx(),
                    center = Offset(stepX * elapsedDays, y(cumulative.last())),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = prettyDate(startDate, pattern = "dd MMM", simplifyIfToday = false),
                    style = MaterialTheme.typography.labelSmall,
                )
                Text(
                    text = prettyDate(finishDate, pattern = "dd MMM", simplifyIfToday = false),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendItem(
                    color = spentColor,
                    text = "${stringResource(R.string.pace_spent)} ${
                        numberFormat(context, pace.spent, currency = currency)
                    }",
                )
                Spacer(Modifier.width(16.dp))
                LegendItem(
                    color = allowedColor,
                    text = "${stringResource(R.string.pace_allowed)} ${
                        numberFormat(context, pace.allowed, currency = currency)
                    }",
                )
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}

@Preview
@Composable
private fun PreviewPaceChartCard() {
    BuckwheatTheme {
        PaceChartCard(
            budget = BigDecimal(100),
            spends = listOf(
                Transaction(TransactionType.SPENT, BigDecimal(12), LocalDate.now().minusDays(3).toDate()),
                Transaction(TransactionType.SPENT, BigDecimal(30), LocalDate.now().minusDays(1).toDate()),
            ),
            startDate = LocalDate.now().minusDays(4).toDate(),
            finishDate = LocalDate.now().plusDays(2).toDate(),
            currency = ExtendCurrency.none(),
        )
    }
}
