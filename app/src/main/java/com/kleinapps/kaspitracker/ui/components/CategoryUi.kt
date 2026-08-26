package com.kleinapps.kaspitracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kleinapps.kaspitracker.data.Category

/**
 * Fixed per-category hue assignment (never cycled/generated). The 10-color set
 * was validated for both themes with scripts/validate_palette.js (dataviz skill):
 * lightness band, chroma floor, CVD and normal-vision separation, contrast —
 * all pass; categories are always rendered with icon + label, never color alone.
 */
val Category.color: Color
    get() = when (this) {
        Category.GROCERIES -> Color(0xFF43A047)
        Category.CAFE -> Color(0xFFD97706)
        Category.TRANSPORT -> Color(0xFF2E7CF6)
        Category.SHOPPING -> Color(0xFFE23D77)
        Category.HEALTH -> Color(0xFF0D9488)
        Category.ENTERTAINMENT -> Color(0xFF9333EA)
        Category.UTILITIES -> Color(0xFFA16207)
        Category.EDUCATION -> Color(0xFF6366F1)
        Category.TRANSFER -> Color(0xFFEA580C)
        Category.INCOME -> Color(0xFF22C55E)
        Category.OTHER -> Color(0xFF5273B3)
    }

val Category.icon: ImageVector
    get() = when (this) {
        Category.GROCERIES -> Icons.Filled.LocalGroceryStore
        Category.CAFE -> Icons.Filled.Restaurant
        Category.TRANSPORT -> Icons.Filled.DirectionsCar
        Category.SHOPPING -> Icons.Filled.ShoppingBag
        Category.HEALTH -> Icons.Filled.MedicalServices
        Category.ENTERTAINMENT -> Icons.Filled.Movie
        Category.UTILITIES -> Icons.Filled.Bolt
        Category.EDUCATION -> Icons.Filled.School
        Category.TRANSFER -> Icons.Filled.SwapHoriz
        Category.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
        Category.OTHER -> Icons.Filled.Category
    }

/** Round tinted icon badge — the visual anchor of every transaction row. */
@Composable
fun CategoryAvatar(
    category: Category,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(category.color.copy(alpha = 0.16f), CircleShape),
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

/**
 * Chip grid for picking a spending category. Shared by the manual edit sheet
 * and the "needs review" queue so both offer the exact same choices.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPickerGrid(
    selected: Category?,
    onSelect: (Category) -> Unit,
    modifier: Modifier = Modifier,
    categories: List<Category> = Category.entries.filter { it != Category.INCOME }
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        categories.forEach { c ->
            FilterChip(
                selected = selected == c,
                onClick = { onSelect(c) },
                label = { Text(c.label) },
                leadingIcon = {
                    Icon(
                        imageVector = c.icon,
                        contentDescription = null,
                        tint = c.color,
                        modifier = Modifier.height(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = c.color.copy(alpha = 0.20f)
                )
            )
        }
    }
}
