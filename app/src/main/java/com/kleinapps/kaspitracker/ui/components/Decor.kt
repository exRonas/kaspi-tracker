package com.kleinapps.kaspitracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.util.formatMoneyWhole

/** Small rounded label on a tint: "+12% к прошлому месяцу", "не в статистике". */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = Kaspi.colors.ink2,
    background: Color = Kaspi.colors.surf2,
    icon: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        icon?.invoke()
        Text(text = text, style = Kaspi.type.label, color = tint, maxLines = 1)
    }
}

/** "♥ общее" — marks an operation spent on both of us, in the "us" gold. */
@Composable
fun SharedBadge(modifier: Modifier = Modifier) {
    Pill(
        text = "общее",
        modifier = modifier,
        tint = Kaspi.colors.us,
        background = Kaspi.colors.tint(Kaspi.colors.us, 0.16f),
        icon = {
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = Kaspi.colors.us, modifier = Modifier.size(12.dp))
        }
    )
}

/** Outlined pill for rows left out of the totals. */
@Composable
fun ExcludedBadge(modifier: Modifier = Modifier) {
    Text(
        text = "не в статистике",
        style = Kaspi.type.label.copy(fontSize = 11.sp),
        color = Kaspi.colors.ink3,
        maxLines = 1,
        modifier = modifier
            .border(1.dp, Kaspi.colors.line, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 1.dp)
    )
}

/** Round initial in a person's colour: "Н", "А". [ring] draws the "selected" outline. */
@Composable
fun PersonAvatar(
    initial: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    ring: Boolean = false
) {
    Box(
        modifier = modifier
            .size(size)
            .then(if (ring) Modifier.border(2.dp, color, CircleShape).padding(3.dp) else Modifier)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            style = Kaspi.type.label.copy(fontSize = (size.value * 0.42f).sp),
            color = Kaspi.colors.onAcc
        )
    }
}

/** Ring of the month's days so far, with the day number in the middle. */
@Composable
fun DayRing(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    color: Color = Kaspi.colors.accent
) {
    val track = Kaspi.colors.surf2
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(800), label = "ring")
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(stroke))
            drawArc(
                color, -90f, 360f * animated, false, Offset(inset, inset), arc,
                style = Stroke(stroke, cap = StrokeCap.Butt)
            )
        }
        Text(text = label, style = Kaspi.type.bodyL.copy(fontWeight = Kaspi.type.titleM.fontWeight), color = Kaspi.colors.ink)
    }
}

/** Thin share bar on a track: a category's part of the month. */
@Composable
fun ShareBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Kaspi.colors.surf2)
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

/**
 * Money that counts up to its value rather than snapping to it. Every frame
 * shows a real figure on the way to the right one.
 */
@Composable
fun AnimatedMoney(
    amount: Double,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Kaspi.colors.ink,
    prefix: String = ""
) {
    val animated by animateFloatAsState(amount.toFloat(), tween(700), label = "money")
    Text(
        text = prefix + formatMoneyWhole(animated.toDouble()),
        modifier = modifier,
        style = style,
        color = color,
        maxLines = 1
    )
}
