package com.kleinapps.kaspitracker.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.util.formatMoneyWhole

data class BarEntry(val label: String, val value: Double)

/**
 * Columns with one emphasized bar (the peak, or a chosen one) in the full
 * accent and the rest in a pale tint of it, so the shape of the period reads at
 * a glance. Optionally a dashed average line with its legend under the axis.
 */
@Composable
fun BarChart(
    bars: List<BarEntry>,
    modifier: Modifier = Modifier,
    height: Dp = 112.dp,
    color: Color = Kaspi.colors.accent,
    highlight: Int? = bars.indices.maxByOrNull { bars[it].value }?.takeIf { bars[it].value > 0 },
    showAverage: Boolean = false,
    /** Index whose label is bold — "today" or "this week". */
    emphasizedLabel: Int? = null
) {
    if (bars.isEmpty()) return
    val max = (bars.maxOfOrNull { it.value } ?: 0.0).coerceAtLeast(1.0)
    val pale = color.copy(alpha = 0.32f).compositeOver(Kaspi.colors.surf)
    val track = Kaspi.colors.surf2
    val averageColor = Kaspi.colors.ink3
    val average = bars.map { it.value }.average()

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val gap = 6.dp.toPx()
            val barWidth = (size.width - gap * (bars.size - 1)) / bars.size
            val radius = CornerRadius(minOf(6.dp.toPx(), barWidth / 2))
            bars.forEachIndexed { i, bar ->
                val x = i * (barWidth + gap)
                val h = (bar.value / max).toFloat() * size.height
                if (bar.value <= 0) {
                    // An empty day still shows where it is.
                    drawRoundRect(track, Offset(x, size.height - 3.dp.toPx()), Size(barWidth, 3.dp.toPx()), radius)
                } else {
                    drawRoundRect(
                        color = if (i == highlight) color else pale,
                        topLeft = Offset(x, size.height - h),
                        size = Size(barWidth, h.coerceAtLeast(3.dp.toPx())),
                        cornerRadius = radius
                    )
                }
            }
            if (showAverage && average > 0) {
                val y = size.height - (average / max).toFloat() * size.height
                drawLine(
                    color = averageColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            bars.forEachIndexed { i, bar ->
                Text(
                    text = bar.label,
                    style = Kaspi.type.label,
                    color = if (i == emphasizedLabel) Kaspi.colors.ink else Kaspi.colors.ink2,
                    fontWeight = if (i == emphasizedLabel) FontWeight.SemiBold else null,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (showAverage && average > 0) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(16.dp).height(2.dp)) {
                    drawLine(
                        averageColor, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "в среднем ${formatMoneyWhole(average)}",
                    style = Kaspi.type.label,
                    color = Kaspi.colors.ink2
                )
            }
        }
    }
}

/** Month pairs: spending in the accent next to income in green, with a legend. */
@Composable
fun PairedBarChart(
    spent: List<Double>,
    income: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    /** Index drawn in the full accent — the current month. */
    highlight: Int? = null
) {
    val accent = Kaspi.colors.accent
    val pale = accent.copy(alpha = 0.55f).compositeOver(Kaspi.colors.surf)
    val pos = Kaspi.colors.pos.copy(alpha = 0.75f).compositeOver(Kaspi.colors.surf)
    val axis = Kaspi.colors.line
    val max = (spent + income).maxOrNull()?.coerceAtLeast(1.0) ?: 1.0

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LegendDot(accent, "Траты")
            Spacer(Modifier.width(12.dp))
            LegendDot(pos, "Доход")
        }
        Spacer(Modifier.height(12.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val groups = labels.size
            val groupWidth = size.width / groups
            val bar = (groupWidth * 0.3f).coerceAtMost(8.dp.toPx())
            val radius = CornerRadius(bar / 2)
            for (i in 0 until groups) {
                val center = groupWidth * i + groupWidth / 2
                val s = spent.getOrElse(i) { 0.0 }
                val inc = income.getOrElse(i) { 0.0 }
                if (s > 0) {
                    val h = (s / max).toFloat() * size.height
                    drawRoundRect(if (i == highlight) accent else pale, Offset(center - bar - 1.dp.toPx(), size.height - h), Size(bar, h), radius)
                }
                if (inc > 0) {
                    val h = (inc / max).toFloat() * size.height
                    drawRoundRect(pos, Offset(center + 1.dp.toPx(), size.height - h), Size(bar, h), radius)
                }
            }
            drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    text = it,
                    style = Kaspi.type.label,
                    color = Kaspi.colors.ink2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
    Spacer(Modifier.width(6.dp))
    Text(text = label, style = Kaspi.type.label, color = Kaspi.colors.ink2)
}

data class DonutSlice(val label: String, val value: Double, val color: Color)

/** Ring of spending by category with the total in the middle; the list below is the legend. */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    total: Double,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp
) {
    val positive = slices.filter { it.value > 0 }
    if (positive.isEmpty() || total <= 0) return
    val track = Kaspi.colors.surf2
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 22.dp.toPx()
            val diameter = this.size.minDimension - stroke
            val topLeft = Offset((this.size.width - diameter) / 2, (this.size.height - diameter) / 2)
            val arc = Size(diameter, diameter)
            drawArc(track, 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
            val gap = (1.5.dp.toPx() / (Math.PI.toFloat() * diameter)) * 360f
            var start = -90f
            positive.forEach { slice ->
                val sweep = (slice.value / total * 360f).toFloat()
                drawArc(
                    slice.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.8f), false, topLeft, arc,
                    style = Stroke(stroke, cap = StrokeCap.Butt)
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "ВСЕГО", style = Kaspi.type.overline, color = Kaspi.colors.ink2)
            Text(text = formatMoneyWhole(total), style = Kaspi.type.amountM, color = Kaspi.colors.ink)
            Text(text = "категорий: ${positive.size}", style = Kaspi.type.label, color = Kaspi.colors.ink3)
        }
    }
}
