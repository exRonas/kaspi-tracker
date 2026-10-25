package com.kleinapps.kaspitracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.BarChart
import com.kleinapps.kaspitracker.ui.components.BarEntry
import com.kleinapps.kaspitracker.ui.components.EmptyState
import com.kleinapps.kaspitracker.ui.components.SectionHeader
import com.kleinapps.kaspitracker.ui.components.StatTile
import com.kleinapps.kaspitracker.ui.theme.Income
import com.kleinapps.kaspitracker.ui.theme.Spend
import com.kleinapps.kaspitracker.util.MonthTotal
import com.kleinapps.kaspitracker.util.OverallStats
import com.kleinapps.kaspitracker.util.YearStats
import com.kleinapps.kaspitracker.util.formatMoneyWhole
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.round

private val MONTHS_SHORT = listOf(
    "Янв", "Фев", "Мар", "Апр", "Май", "Июн", "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"
)
private val MONTHS_FULL = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
)
private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
private val dayFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
private val weekDayFormat = SimpleDateFormat("d", Locale("ru"))
private val weekFormat = SimpleDateFormat("d MMMM", Locale("ru"))

/**
 * The year month by month, and all-time averages. Tapping a past month opens
 * History filtered to it, so every number can be traced to its operations.
 */
@Composable
fun StatsScreen(
    onOpenMonth: (from: Long, to: Long, label: String) -> Unit = { _, _, _ -> },
    viewModel: StatsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val year = state.year
    val overall = state.overall

    if (overall == null || year == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Filled.Insights,
                title = "Пока нечего считать",
                subtitle = "Статистика появится после первых операций"
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            YearPicker(
                year = year.year,
                years = state.years,
                onSelect = viewModel::selectYear
            )
        }
        item { YearSummaryCard(year) }
        item {
            StatsCard(title = "По месяцам") {
                BarChart(
                    bars = year.months.map {
                        BarEntry(MONTHS_SHORT[it.month], it.spent, detail = MONTHS_FULL[it.month])
                    }
                )
                Spacer(Modifier.height(8.dp))
                year.months.forEach { month ->
                    MonthRow(
                        month = month,
                        onClick = {
                            onOpenMonth(
                                month.start,
                                month.end - 1,
                                "${MONTHS_FULL[month.month]} ${year.year}"
                            )
                        }
                    )
                }
            }
        }
        item { AveragesCard(overall) }
        item {
            StatsCard(title = "По дням недели") {
                Text(
                    text = "Сколько в среднем уходит в этот день",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                BarChart(
                    bars = overall.weekdayAverages.mapIndexed { i, v -> BarEntry(WEEKDAYS[i], round(v)) }
                )
            }
        }
        item {
            StatsCard(title = "Последние 12 недель") {
                Text(
                    text = "Под столбцом — число, с которого началась неделя",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                BarChart(
                    bars = overall.lastWeeks.map {
                        BarEntry(
                            label = weekDayFormat.format(it.start),
                            value = it.spent,
                            detail = "неделя с ${weekFormat.format(it.start)}"
                        )
                    }
                )
            }
        }
        item { AllTimeCard(overall) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun YearPicker(year: Int, years: List<Int>, onSelect: (Int) -> Unit) {
    val index = years.indexOf(year)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(
            onClick = { onSelect(years[index - 1]) },
            enabled = index > 0
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий год")
        }
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        IconButton(
            onClick = { onSelect(years[index + 1]) },
            enabled = index in 0 until years.lastIndex
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий год")
        }
    }
}

@Composable
private fun YearSummaryCard(year: YearStats) {
    StatsCard(title = "Год") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Потрачено",
                value = "− ${formatMoneyWhole(year.spent)}",
                valueColor = Spend,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Получено",
                value = "+ ${formatMoneyWhole(year.income)}",
                valueColor = Income,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Разница",
                value = signed(year.income - year.spent),
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Средний месяц",
                value = year.averageMonthSpent?.let { formatMoneyWhole(it) } ?: "—",
                modifier = Modifier.weight(1f)
            )
        }
        year.priciestMonth?.let { month ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Самый дорогой: ${MONTHS_FULL[month.month].lowercase()} · " +
                    formatMoneyWhole(month.spent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One month in the list under the chart; future months are shown but not tappable. */
@Composable
private fun MonthRow(month: MonthTotal, onClick: () -> Unit) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (month.isFuture) Modifier else Modifier.clickable(onClick = onClick))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = MONTHS_FULL[month.month],
            style = MaterialTheme.typography.bodyLarge,
            color = if (month.isFuture) dim else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (!month.isFuture) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "− ${formatMoneyWhole(month.spent)}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                if (month.income > 0) {
                    Text(
                        text = "+ ${formatMoneyWhole(month.income)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Income
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Открыть операции месяца",
                tint = dim
            )
        }
    }
}

@Composable
private fun AveragesCard(overall: OverallStats) {
    StatsCard(title = "В среднем") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("В день", formatMoneyWhole(overall.perDay), Modifier.weight(1f))
            StatTile("В неделю", formatMoneyWhole(overall.perWeek), Modifier.weight(1f))
            StatTile("В месяц", formatMoneyWhole(overall.perMonth), Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "с ${dayFormat.format(overall.firstDay)} · дней: ${overall.days}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AllTimeCard(overall: OverallStats) {
    StatsCard(title = "За всё время") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(
                label = "Потрачено",
                value = "− ${formatMoneyWhole(overall.totalSpent)}",
                valueColor = Spend,
                modifier = Modifier.weight(1f)
            )
            StatTile(
                label = "Получено",
                value = "+ ${formatMoneyWhole(overall.totalIncome)}",
                valueColor = Income,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        overall.priciestDay?.let { day ->
            Text(
                text = "Самый дорогой день: ${dayFormat.format(day.start)} · " +
                    formatMoneyWhole(day.spent),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
        }
        Text(
            text = "Дней без трат: ${overall.daysWithoutSpending} из ${overall.days}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    AppCard {
        SectionHeader(title = title)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

private fun signed(amount: Double): String =
    if (amount < 0) "− ${formatMoneyWhole(-amount)}" else "+ ${formatMoneyWhole(amount)}"
