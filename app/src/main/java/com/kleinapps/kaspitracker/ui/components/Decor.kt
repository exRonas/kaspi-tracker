package com.kleinapps.kaspitracker.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kleinapps.kaspitracker.util.formatMoneyWhole
import kotlin.math.min

/**
 * Soft coloured light behind the content of a big card.
 *
 * Two off-centre radial blobs painted under everything else — this is what
 * stops a gradient panel from reading as a flat slab. Cheap by design: two
 * radial gradients per frame and no blur pass, so it also works below API 31,
 * where `Modifier.blur` silently does nothing.
 */
@Composable
fun BoxScope.GlowOrbs(
    primary: Color = Color.White,
    secondary: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.matchParentSize()) {
        val first = Offset(size.width * 0.82f, size.height * 0.10f)
        val second = Offset(size.width * 0.06f, size.height * 0.98f)
        val firstRadius = size.minDimension * 0.9f
        val secondRadius = size.minDimension * 0.8f

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.32f), Color.Transparent),
                center = first,
                radius = firstRadius
            ),
            radius = firstRadius,
            center = first
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = 0.24f), Color.Transparent),
                center = second,
                radius = secondRadius
            ),
            radius = secondRadius,
            center = second
        )
    }
}

/**
 * Slow diagonal light sweep, the way light travels over a bank card as it
 * tilts. Twelve seconds per pass: visible when you look at it, never moving
 * fast enough to pull your eye off the numbers.
 */
@Composable
fun BoxScope.ShineSweep(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shine")
    val position by transition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing)),
        label = "shinePosition"
    )
    Box(
        modifier = modifier
            .matchParentSize()
            .drawBehind {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.11f),
                            Color.Transparent
                        ),
                        start = Offset(size.width * (position - 0.22f), 0f),
                        end = Offset(size.width * (position + 0.22f), size.height)
                    )
                )
            }
    )
}

/** Hairline of light down from the top edge — a lit surface, not a rectangle. */
fun Modifier.topHighlight(
    color: Color = Color.White,
    alpha: Float = 0.20f
): Modifier = drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            startY = 0f,
            endY = size.height * 0.55f
        )
    )
}

/**
 * Progress ring with a rounded cap and its own halo. Carries the days left in
 * the month on the hero and budget progress on the budgets screen.
 */
@Composable
fun RingGauge(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 74.dp,
    stroke: Dp = 7.dp,
    trackColor: Color = Color.White.copy(alpha = 0.22f),
    progressColor: Color = Color.White,
    content: @Composable () -> Unit = {}
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900),
        label = "ring"
    )
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = stroke.toPx()
            val inset = strokePx
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // Halo first, so the arc sits on top of its own glow.
            drawArc(
                color = progressColor.copy(alpha = 0.16f),
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx * 2.2f, cap = StrokeCap.Round)
            )
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

/**
 * Money that counts up to its value rather than snapping to it. Every frame
 * shows a real figure on the way to the right one, so nothing false is ever on
 * screen.
 */
@Composable
fun AnimatedMoney(
    amount: Double,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.displayLarge,
    color: Color = Color.Unspecified,
    currency: String = "₸"
) {
    val animated by animateFloatAsState(
        targetValue = amount.toFloat(),
        animationSpec = tween(700),
        label = "money"
    )
    Text(
        text = formatMoneyWhole(animated.toDouble(), currency),
        modifier = modifier,
        style = style,
        color = color,
        maxLines = 1
    )
}

/** Small rounded label — "+12% к прошлому", "перерасход", "раз в месяц". */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = tint,
    background: Color = tint.copy(alpha = 0.16f)
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = contentColor,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

/**
 * Position chip for ordered lists. A numbered list reads as a ranking; the same
 * rows without numbers read as a dump.
 */
@Composable
fun RankBadge(
    position: Int,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(tint.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "$position",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            color = tint
        )
    }
}

/**
 * Shrinks slightly while held — the cue that a card is a button, which is
 * otherwise lost once the ripple is hidden under a clipped background.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.975f
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(120),
        label = "pressScale"
    )
    return this.scale(scale)
}

/** Interaction source for [pressScale], so callers skip the boilerplate. */
@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

/**
 * Trend line small enough to live inside a row: a stroke, a soft fill under it
 * and a dot on the latest point. No axes and no labels — it is a shape, not a
 * chart, and it is there to give the row a pulse.
 */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    if (values.size < 2) return
    Canvas(modifier = modifier) {
        val maximum = values.max()
        val minimum = values.min()
        val span = (maximum - minimum).takeIf { it > 0.0 } ?: 1.0
        val stepX = size.width / (values.size - 1)
        val points = values.mapIndexed { index, value ->
            Offset(
                x = stepX * index,
                y = size.height - ((value - minimum) / span * size.height).toFloat()
            )
        }

        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }

        drawPath(
            path = area,
            brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent))
        )
        drawPath(
            path = line,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawCircle(
            color = color,
            radius = min(3.dp.toPx(), size.height / 6f),
            center = points.last()
        )
    }
}
