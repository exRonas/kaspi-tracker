package com.kleinapps.kaspitracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.ui.theme.Radius

/**
 * Card: a tone step up from the background. No border, no shadow — the design
 * separates surfaces by tone alone.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    color: Color = Kaspi.colors.surf,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.card))
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

/** Title of a card or a group, with an optional text action on the right ("Вся история"). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = Kaspi.type.titleM,
            color = Kaspi.colors.ink,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = Kaspi.type.bodyM.copy(fontWeight = Kaspi.type.titleM.fontWeight),
                color = Kaspi.colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.bar))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
        trailing?.invoke(this)
    }
}

/**
 * Top of a tab: a small kicker line ("Понедельник, 26 октября") over a serif
 * title, with icon actions on the right.
 */
@Composable
fun ScreenHeader(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = kicker, style = Kaspi.type.bodyS, color = Kaspi.colors.ink2)
            Spacer(Modifier.height(4.dp))
            Text(text = title, style = Kaspi.type.headline, color = Kaspi.colors.ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Small labelled number in a tile: "Потрачено за год / −3 201 820 ₸". */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Kaspi.colors.ink,
    caption: String? = null
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.field))
            .background(Kaspi.colors.surf)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(text = label, style = Kaspi.type.label, color = Kaspi.colors.ink2, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(text = value, style = Kaspi.type.amountM, color = valueColor, maxLines = 1)
        caption?.let {
            Text(text = it, style = Kaspi.type.label, color = Kaspi.colors.ink3, maxLines = 1)
        }
    }
}

/** Glyph on its colour at 16 % — tool tiles, banners, list leading icons. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(Kaspi.colors.tint(tint), RoundedCornerShape(Radius.icon)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** Tile in the two-column "Инструменты" grid: glyph, title, one or two lines of explanation. */
@Composable
fun ToolTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier, onClick = onClick) {
        Icon(icon, contentDescription = null, tint = Kaspi.colors.accent, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(16.dp))
        Text(text = title, style = Kaspi.type.titleM, color = Kaspi.colors.ink, maxLines = 2)
        Spacer(Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = Kaspi.type.bodyS,
            color = Kaspi.colors.ink2,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A plain row in a card: glyph, text, chevron — "Показать мой код", "Журнал уведомлений". */
@Composable
fun LinkRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Kaspi.colors.ink2,
    titleColor: Color = Kaspi.colors.ink,
    showChevron: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text = title, style = Kaspi.type.bodyL, color = titleColor, modifier = Modifier.weight(1f))
        if (showChevron) {
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Kaspi.colors.ink3
            )
        }
    }
}

/**
 * Warning on a warm tint: what is wrong, why it matters, one way to fix it.
 * [tint] defaults to the warning colour; the review banner uses the accent.
 */
@Composable
fun Banner(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = Kaspi.colors.warn,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    AppCard(modifier = modifier, color = Kaspi.colors.tint(tint, 0.12f)) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = Kaspi.type.bodyL.copy(fontWeight = Kaspi.type.titleM.fontWeight), color = Kaspi.colors.ink)
                Spacer(Modifier.height(2.dp))
                Text(text = text, style = Kaspi.type.bodyS, color = Kaspi.colors.ink2)
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(text = actionLabel, onClick = onAction, color = tint)
                }
            }
        }
    }
}

/** Empty state: a tinted glyph over one short explanation, never a bare card. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconBadge(icon = icon, tint = Kaspi.colors.accent, size = 56.dp)
        Spacer(Modifier.height(4.dp))
        Text(text = title, style = Kaspi.type.titleM, color = Kaspi.colors.ink, textAlign = TextAlign.Center)
        Text(text = subtitle, style = Kaspi.type.bodyM, color = Kaspi.colors.ink2, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Hairline between rows inside a card, inset past the leading icon. */
@Composable
fun RowDivider(modifier: Modifier = Modifier, start: Dp = 0.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = start)
            .height(1.dp)
            .background(Kaspi.colors.line)
    )
}
