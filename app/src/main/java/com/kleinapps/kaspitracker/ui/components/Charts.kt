package com.kleinapps.kaspitracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kleinapps.kaspitracker.ui.theme.BarGradient
import com.kleinapps.kaspitracker.ui.theme.BarGradientPeak
import com.kleinapps.kaspitracker.util.formatMoney

data class DonutSlice(val label: String, val value: Double, val color: Color)

/**
 * Donut of spending by category: ring arcs with small gaps between slices,
 * headline total in the center, legend rows below (color chip + label + value)
 * so identity is never carried by color alone.
 */
@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    total: Double,
    modifier: Modifier = Modifier,
    /** Off when the caller already lists the categories under the ring. */
    showLegend: Boolean = true
) {
    val positive = slices.filter { it.value > 0 }
    if (positive.isEmpty() || total <= 0) return
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val stroke = 30.dp.toPx()
                val diameter = size.minDimension - stroke
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                // Faint full ring underneath, so the donut still reads as a ring
                // when one category dominates and the rest are slivers.
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
                // 2dp visual gap between slices, expressed in degrees of this ring.
                val gapDegrees = (2.dp.toPx() / (Math.PI.toFloat() * diameter)) * 360f
                var start = -90f
                positive.forEach { slice ->
                    val sweep = (slice.value / total * 360f).toFloat()
                    val visibleSweep = (sweep - gapDegrees).coerceAtLeast(1f)
                    drawArc(
                        color = slice.color,
                        startAngle = start + gapDegrees / 2f,
                        sweepAngle = visibleSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt)
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ВСЕГО",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatMoney(total),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "категорий: ${positive.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!showLegend) return
        Column(modifier = Modifier.padding(top = 12.dp)) {
            positive.forEach { slice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawRoundRect(
                            color = slice.color,
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                    Text(
                        text = slice.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(slice.value / total * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatMoney(slice.value),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/**
 * One bar. [label] sits under the bar and must stay short; [detail] names the
 * bar in full in the line above the chart when it is picked or the peak.
 */
data class BarEntry(val label: String, val value: Double, val detail: String = label)

/**
 * Rounded bar chart with a background track and labels under each bar. Tap a
 * bar to pin its exact amount above the chart — bars alone don't say the
 * number; with nothing selected the tallest day is called out instead.
 */
@Composable
fun BarChart(
    bars: List<BarEntry>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    gradient: List<Color> = BarGradient,
    peakGradient: List<Color> = BarGradientPeak,
    height: Dp = 128.dp
) {
    if (bars.isEmpty()) return
    val max = (bars.maxOfOrNull { it.value } ?: 0.0).coerceAtLeast(1.0)
    val track = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    val averageColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val peakIndex = bars.indices.maxByOrNull { bars[it].value }
    var selected by remember(bars) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        Text(
            text = selected?.let { i -> "${bars[i].detail} · ${formatMoney(bars[i].value)}" }
                ?: peakIndex
                    ?.takeIf { bars[it].value > 0 }
                    ?.let { i -> "Пик: ${bars[i].detail} · ${formatMoney(bars[i].value)}" }
                ?: " ",
            style = MaterialTheme.typography.labelLarge,
            color = if (selected != null) barColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(bars) {
                    detectTapGestures { offset ->
                        val count = bars.size
                        val gap = 10.dp.toPx()
                        val barWidth = (size.width - gap * (count - 1)) / count
                        val index = (offset.x / (barWidth + gap)).toInt().coerceIn(0, count - 1)
                        selected = if (selected == index) null else index
                    }
                }
        ) {
            val count = bars.size
            val gap = 10.dp.toPx()
            val barWidth = (size.width - gap * (count - 1)) / count
            val radius = CornerRadius(barWidth / 2.6f, barWidth / 2.6f)
            bars.forEachIndexed { i, bar ->
                val x = i * (barWidth + gap)
                val dim = selected != null && selected != i
                drawRoundRect(
                    color = track,
                    topLeft = Offset(x, 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = radius
                )
                if (bar.value <= 0) return@forEachIndexed
                val barHeight = (bar.value / max * size.height).toFloat().coerceAtLeast(6f)
                val isPeak = i == peakIndex
                // The peak day gets the brighter ramp, so the week has a shape
                // you can read before you read any number.
                val colors = (if (isPeak) peakGradient else gradient)
                    .map { if (dim) it.copy(alpha = 0.35f) else it }
                if (isPeak && !dim) {
                    // Halo under the tallest bar — depth without a blur pass.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(colors.first().copy(alpha = 0.28f), Color.Transparent),
                            startY = size.height - barHeight,
                            endY = size.height
                        ),
                        topLeft = Offset(x - gap * 0.35f, size.height - barHeight - gap * 0.35f),
                        size = Size(barWidth + gap * 0.7f, barHeight + gap * 0.35f),
                        cornerRadius = radius
                    )
                }
                // Lighter at the top: the bar reads as lit rather than painted.
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = colors,
                        startY = size.height - barHeight,
                        endY = size.height
                    ),
                    topLeft = Offset(x, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius
                )
            }

            // Dashed average: turns seven bars into "today versus usual".
            val average = bars.map { it.value }.filter { it > 0 }.average()
            if (!average.isNaN() && average > 0) {
                val y = size.height - (average / max * size.height).toFloat()
                drawLine(
                    color = averageColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 6.dp.toPx())
                    )
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            bars.forEachIndexed { i, bar ->
                // Equal weights centre every label under its own bar, which
                // SpaceBetween does not do for the first and last one.
                Text(
                    text = bar.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        selected == i -> barColor
                        selected == null && peakIndex == i -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
