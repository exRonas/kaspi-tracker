package com.kleinapps.kaspitracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.ui.components.BarChart
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.SectionHeader
import com.kleinapps.kaspitracker.ui.components.BarEntry
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.CategoryAvatar
import com.kleinapps.kaspitracker.ui.components.IconBadge
import com.kleinapps.kaspitracker.ui.components.Pill
import com.kleinapps.kaspitracker.ui.components.DonutChart
import com.kleinapps.kaspitracker.ui.components.DonutSlice
import com.kleinapps.kaspitracker.ui.components.color
import com.kleinapps.kaspitracker.ui.theme.Income
import com.kleinapps.kaspitracker.ui.theme.Spend
import com.kleinapps.kaspitracker.util.formatMoney

@Composable
fun AnalyticsScreen(
    onOpenCategory: (Category, Long, Long, String) -> Unit = { _, _, _, _ -> },
    viewModel: AnalyticsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.period == period,
                        onClick = { viewModel.selectPeriod(period) },
                        label = { Text(period.label) }
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SummaryCard(
                    title = "Потрачено",
                    value = "− ${formatMoney(state.spent)}",
                    valueColor = Spend,
                    icon = Icons.Filled.SouthWest,
                    modifier = Modifier.weight(1f)
                )
                SummaryCard(
                    title = "Получено",
                    value = "+ ${formatMoney(state.income)}",
                    valueColor = Income,
                    icon = Icons.Filled.NorthEast,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (state.byCategory.isNotEmpty()) {
            item {
                SectionCard(title = "По категориям") {
                    DonutChart(
                        slices = state.byCategory.map {
                            DonutSlice(it.category.label, it.amount, it.category.color)
                        },
                        total = state.spent,
                        // The tappable breakdown below is the legend.
                        showLegend = false
                    )
                    Spacer(Modifier.height(6.dp))
                    state.byCategory.forEach { entry ->
                        CategoryBreakdownRow(
                            entry = entry,
                            total = state.spent,
                            onClick = {
                                onOpenCategory(
                                    entry.category, state.from, state.to, state.period.label
                                )
                            }
                        )
                    }
                }
            }
        }

        if (state.topMerchants.isNotEmpty()) {
            item {
                SectionCard(title = "Топ мерчантов") {
                    state.topMerchants.forEach { merchant ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = merchant.merchant,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = "операций: ${merchant.count}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = formatMoney(merchant.amount),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "Сравнение месяцев") {
                MonthComparison(thisMonth = state.thisMonth, prevMonth = state.prevMonth)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier, contentPadding = 16.dp, accent = valueColor) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = icon, tint = valueColor, size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    AppCard {
        SectionHeader(title = title)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun MonthComparison(thisMonth: Double, prevMonth: Double) {
    Column {
        val diff = thisMonth - prevMonth
        val diffText = when {
            prevMonth <= 0 && thisMonth <= 0 -> "Нет данных для сравнения"
            diff > 0 -> "На ${formatMoney(diff)} больше, чем в прошлом месяце"
            diff < 0 -> "На ${formatMoney(-diff)} меньше, чем в прошлом месяце"
            else -> "Ровно как в прошлом месяце"
        }
        BarChart(
            bars = listOf(
                BarEntry("Прошлый", prevMonth),
                BarEntry("Этот", thisMonth)
            ),
            height = 90.dp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = diffText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (diff > 0) Spend else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One category under the donut. Tapping it opens History filtered to exactly
 * this category and period, so the number can always be traced to the
 * operations behind it.
 */
@Composable
private fun CategoryBreakdownRow(
    entry: CategorySum,
    total: Double,
    onClick: () -> Unit
) {
    val share = if (total > 0) (entry.amount / total * 100).toInt() else 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryAvatar(entry.category, size = 34.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.category.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(8.dp))
                Pill(text = "$share%", tint = entry.category.color)
            }
            Spacer(Modifier.height(6.dp))
            // Share of the period as a bar, so the list is scannable by shape
            // before it is read by number.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((share / 100f).coerceIn(0.02f, 1f))
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(entry.category.color)
                )
            }
        }
        Text(
            text = formatMoney(entry.amount),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Открыть операции",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
