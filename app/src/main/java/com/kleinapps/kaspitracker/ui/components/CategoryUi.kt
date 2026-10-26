package com.kleinapps.kaspitracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.ui.theme.Radius

/** The category's colour in the current theme (fixed per category, never generated). */
val Category.color: Color
    @Composable @ReadOnlyComposable get() = Kaspi.colors.category(this)

val Category.icon: ImageVector
    get() = when (this) {
        Category.GROCERIES -> Icons.Outlined.ShoppingBasket
        Category.CAFE -> Icons.Outlined.Restaurant
        Category.TRANSPORT -> Icons.Outlined.DirectionsBus
        Category.SHOPPING -> Icons.Outlined.ShoppingBag
        Category.HEALTH -> Icons.Outlined.Medication
        Category.ENTERTAINMENT -> Icons.Outlined.TheaterComedy
        Category.UTILITIES -> Icons.Outlined.Home
        Category.EDUCATION -> Icons.Outlined.School
        Category.TRANSFER -> Icons.Outlined.SwapHoriz
        Category.INCOME -> Icons.Outlined.Savings
        Category.OTHER -> Icons.Outlined.Category
    }

/** Rounded tile with the category glyph on its colour at 16 % — the anchor of every row. */
@Composable
fun CategoryAvatar(
    category: Category,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(Kaspi.colors.tint(category.color), RoundedCornerShape(Radius.icon)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = category.label,
            tint = category.color,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

/** Small chip "glyph + name" on the category's tint — used in the rules list. */
@Composable
fun CategoryChip(category: Category, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Kaspi.colors.tint(category.color, 0.18f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(category.icon, contentDescription = null, tint = category.color, modifier = Modifier.size(16.dp))
        Text(
            text = category.label,
            style = Kaspi.type.label,
            color = Kaspi.colors.ink,
            maxLines = 1
        )
    }
}

/**
 * Four-column grid of round category buttons, as in the design. Shared by the
 * add form, the edit sheet, the review queue and bulk recategorizing, so all
 * offer the exact same choices.
 */
@Composable
fun CategoryPickerGrid(
    selected: Category?,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
    categories: List<Category> = Category.entries.toList()
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        categories.chunked(4).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { category ->
                    CategoryCell(
                        category = category,
                        selected = category == selected,
                        onClick = { onSelect(category) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryCell(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = category.color
    val shape = RoundedCornerShape(Radius.field)
    Column(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier
                        .background(Kaspi.colors.tint(color, 0.12f))
                        .border(2.dp, color, shape)
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Kaspi.colors.tint(color, if (selected) 0.28f else 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(category.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = category.label,
            style = Kaspi.type.label,
            color = Kaspi.colors.ink,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}
