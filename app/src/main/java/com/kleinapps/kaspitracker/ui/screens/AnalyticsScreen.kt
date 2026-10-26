package com.kleinapps.kaspitracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.CategoryAvatar
import com.kleinapps.kaspitracker.ui.components.DonutChart
import com.kleinapps.kaspitracker.ui.components.DonutSlice
import com.kleinapps.kaspitracker.ui.components.EmptyState
import com.kleinapps.kaspitracker.ui.components.FilterPill
import com.kleinapps.kaspitracker.ui.components.ScreenHeader
import com.kleinapps.kaspitracker.ui.components.SectionHeader
import com.kleinapps.kaspitracker.ui.components.ShareBar
import com.kleinapps.kaspitracker.ui.components.color
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.util.formatMoneyWhole
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(
    onOpenCategory: (Category, Long, Long, String) -> Unit = { _, _, _, _ -> },
    headerActions: @Composable () -> Unit = {},
    viewModel: AnalyticsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader(kicker = "Куда уходят деньги", title = "Аналитика") { headerActions() } }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsPeriod.entries.forEach { period ->
                    FilterPill(
                        label = period.label,
                        selected = state.period == period,
                        onClick = { viewModel.selectPeriod(period) }
                    )
                }
            }
        }
        item { BalanceCard(spent = state.spent, income = state.income) }
        item { MonthComparison(thisMonth = state.thisMonth, prevMonth = state.prevMonth) }

        if (state.byCategory.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.DonutLarge,
                    title = "За этот период трат нет",
                    subtitle = "Выбери другой период или дождись первых операций",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            item {
                AppCard {
                    SectionHeader(title = "По категориям")
                    DonutChart(
                        slices = state.byCategory.map { DonutSlice(it.category.label, it.amount, it.category.color) },
                        total = state.spent
                    )
                    state.byCategory.forEach { entry ->
                        CategoryRow(entry, state.spent) {
                            onOpenCategory(entry.category, state.from, state.to, state.period.label)
                        }
                    }
                }
            }
        }

        if (state.topMerchants.isNotEmpty()) {
            item {
                AppCard {
                    SectionHeader(title = "Топ мерчантов")
                    Spacer(Modifier.height(4.dp))
                    state.topMerchants.forEachIndexed { index, merchant ->
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = Kaspi.type.amountM,
                                color = Kaspi.colors.ink2,
                                modifier = Modifier.width(28.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = merchant.merchant,
                                    style = Kaspi.type.bodyL,
                                    color = Kaspi.colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(text = "операций: ${merchant.count}", style = Kaspi.type.label, color = Kaspi.colors.ink2)
                            }
                            Text(text = formatMoneyWhole(merchant.amount), style = Kaspi.type.money, color = Kaspi.colors.ink)
                        }
                    }
                }
            }
        }
    }
}

/** Spent vs received over the period as one split bar, with what is left over. */
@Composable
private fun BalanceCard(spent: Double, income: Double) {
    val total = spent + income
    AppCard {
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Потрачено", style = Kaspi.type.label, color = Kaspi.colors.ink2)
                Text(text = "−" + formatMoneyWhole(spent), style = Kaspi.type.titleL, color = Kaspi.colors.ink)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Получено", style = Kaspi.type.label, color = Kaspi.colors.ink2)
                Text(text = "+" + formatMoneyWhole(income), style = Kaspi.type.titleL, color = Kaspi.colors.pos)
            }
        }
        if (total > 0) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (spent > 0) ShareBar(1f, Kaspi.colors.accent, Modifier.weight(spent.toFloat()))
                if (income > 0) ShareBar(1f, Kaspi.colors.pos, Modifier.weight(income.toFloat()))
            }
            Spacer(Modifier.height(8.dp))
            val left = income - spent
            Text(
                text = if (left >= 0) "Осталось ${formatMoneyWhole(left)}" else "Потрачено больше полученного на ${formatMoneyWhole(-left)}",
                style = Kaspi.type.label,
                color = Kaspi.colors.ink2
            )
        }
    }
}

@Composable
private fun MonthComparison(thisMonth: Double, prevMonth: Double) {
    val max = maxOf(thisMonth, prevMonth).coerceAtLeast(1.0)
    AppCard {
        SectionHeader(title = "Сравнение месяцев")
        Spacer(Modifier.height(12.dp))
        ComparisonLine("Этот", thisMonth, (thisMonth / max).toFloat(), Kaspi.colors.accent)
        Spacer(Modifier.height(8.dp))
        ComparisonLine("Прошлый", prevMonth, (prevMonth / max).toFloat(), Kaspi.colors.ink3)
        Spacer(Modifier.height(12.dp))
        val diff = thisMonth - prevMonth
        Text(
            text = when {
                prevMonth == 0.0 -> "Нет данных для сравнения"
                abs(diff) < 1 -> "Ровно как в прошлом месяце"
                diff > 0 -> "На ${formatMoneyWhole(diff)} больше, чем в прошлом месяце"
                else -> "На ${formatMoneyWhole(-diff)} меньше, чем в прошлом месяце"
            },
            style = Kaspi.type.bodyM,
            color = Kaspi.colors.ink
        )
    }
}

@Composable
private fun ComparisonLine(label: String, amount: Double, share: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, style = Kaspi.type.bodyS, color = Kaspi.colors.ink2, modifier = Modifier.width(64.dp))
        ShareBar(fraction = share, color = color, modifier = Modifier.weight(1f), height = 10.dp)
        Text(
            text = formatMoneyWhole(amount),
            style = Kaspi.type.money,
            color = Kaspi.colors.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.width(100.dp)
        )
    }
}

/**
 * One category under the donut. Tapping it opens History filtered to exactly
 * this category and period, so the number can always be traced.
 */
@Composable
private fun CategoryRow(entry: CategorySum, total: Double, onClick: () -> Unit) {
    val share = if (total > 0) (entry.amount / total).toFloat() else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryAvatar(entry.category)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row {
                Text(
                    text = entry.category.label,
                    style = Kaspi.type.bodyL,
                    color = Kaspi.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(text = formatMoneyWhole(entry.amount), style = Kaspi.type.money, color = Kaspi.colors.ink)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShareBar(fraction = share, color = entry.category.color, modifier = Modifier.weight(1f))
                Text(
                    text = "${(share * 100).roundToInt()}%",
                    style = Kaspi.type.label,
                    color = Kaspi.colors.ink2,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp)
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "Открыть операции",
            tint = Kaspi.colors.ink3
        )
    }
}
