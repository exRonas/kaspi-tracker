package com.kleinapps.kaspitracker.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.BarChart
import com.kleinapps.kaspitracker.ui.components.BarEntry
import com.kleinapps.kaspitracker.ui.components.EmptyState
import com.kleinapps.kaspitracker.ui.components.PairedBarChart
import com.kleinapps.kaspitracker.ui.components.RowDivider
import com.kleinapps.kaspitracker.ui.components.ScreenHeader
import com.kleinapps.kaspitracker.ui.components.SectionHeader
import com.kleinapps.kaspitracker.ui.components.ShareBar
import com.kleinapps.kaspitracker.ui.components.StatTile
import com.kleinapps.kaspitracker.ui.components.TabSwitch
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.util.MonthTotal
import com.kleinapps.kaspitracker.util.OverallStats
import com.kleinapps.kaspitracker.util.YearStats
import com.kleinapps.kaspitracker.util.formatMoneyWhole
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.round

private val MONTHS_LETTER = listOf("Я", "Ф", "М", "А", "М", "И", "И", "А", "С", "О", "Н", "Д")
private val MONTHS_FULL = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
)
private val WEEKDAYS = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
/** "Дороже всего — субботы". */
private val WEEKDAYS_PLURAL = listOf(
    "понедельники", "вторники", "среды", "четверги", "пятницы", "субботы", "воскресенья"
)
private val dayFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
private val shortDayFormat = SimpleDateFormat("d MMMM", Locale("ru"))

private enum class StatsMode { MONTHS, GENERAL }

/**
 * The year month by month, and habits over the whole history. Tapping a past
 * month opens History filtered to it, so every number can be traced.
 */
@Composable
fun StatsScreen(
    onOpenMonth: (from: Long, to: Long, label: String) -> Unit = { _, _, _ -> },
    headerActions: @Composable () -> Unit = {},
    viewModel: StatsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(StatsMode.MONTHS) }
    val year = state.year
    val overall = state.overall

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader(kicker = "Год и привычки", title = "Статистика") { headerActions() } }
        item {
            TabSwitch(
                options = listOf(StatsMode.MONTHS to "По месяцам", StatsMode.GENERAL to "Общая"),
                selected = mode,
                onSelect = { mode = it }
            )
        }

        if (overall == null || year == null) {
            item {
                EmptyState(
                    icon = Icons.Outlined.CalendarMonth,
                    title = "Пока нечего считать",
                    subtitle = "Статистика появится после первых операций",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else if (mode == StatsMode.MONTHS) {
            item { YearPicker(year.year, state.years, viewModel::selectYear) }
            item { YearTiles(year) }
            item {
                AppCard {
                    PairedBarChart(
                        spent = year.months.map { it.spent },
                        income = year.months.map { it.income },
                        labels = MONTHS_LETTER,
                        highlight = year.months.indexOfLast { !it.isFuture }
                    )
                }
            }
            item {
                MonthList(year) { month ->
                    onOpenMonth(month.start, month.end - 1, "${MONTHS_FULL[month.month]} ${year.year}")
                }
            }
        } else {
            item { AveragesRow(overall) }
            item { WeekdayCard(overall) }
            item { WeeksCard(overall) }
            item { AllTimeCard(overall) }
            item {
                Text(
                    text = "Считается с ${dayFormat.format(overall.firstDay)} — с первой записанной операции",
                    style = Kaspi.type.label,
                    color = Kaspi.colors.ink2,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun YearPicker(year: Int, years: List<Int>, onSelect: (Int) -> Unit) {
    val index = years.indexOf(year)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = { onSelect(years[index - 1]) }, enabled = index > 0) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Предыдущий год")
        }
        Text(
            text = year.toString(),
            style = Kaspi.type.titleL,
            color = Kaspi.colors.ink,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        IconButton(onClick = { onSelect(years[index + 1]) }, enabled = index in 0 until years.lastIndex) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Следующий год")
        }
    }
}

@Composable
private fun YearTiles(year: YearStats) {
    val activeMonths = year.months.count { !it.isFuture && (it.spent > 0 || it.income > 0) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Потрачено за год", "−" + formatMoneyWhole(year.spent), Modifier.weight(1f))
            StatTile("Получено", "+" + formatMoneyWhole(year.income), Modifier.weight(1f), valueColor = Kaspi.colors.pos)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(
                label = "Средний месяц",
                value = year.averageMonthSpent?.let { formatMoneyWhole(it) } ?: "—",
                modifier = Modifier.weight(1f),
                caption = if (activeMonths > 0) "за $activeMonths мес." else null
            )
            StatTile(
                label = "Самый дорогой",
                value = year.priciestMonth?.let { MONTHS_FULL[it.month] } ?: "—",
                modifier = Modifier.weight(1f),
                caption = year.priciestMonth?.let { formatMoneyWhole(it.spent) }
            )
        }
    }
}

@Composable
private fun MonthList(year: YearStats, onOpen: (MonthTotal) -> Unit) {
    val maxSpent = year.months.maxOfOrNull { it.spent }?.takeIf { it > 0 } ?: 1.0
    val current = year.months.indexOfLast { !it.isFuture }
    val currentIsRunning = year.months.getOrNull(current + 1)?.isFuture ?: false
    AppCard(contentPadding = 0.dp) {
        year.months.forEachIndexed { index, month ->
            if (index > 0) RowDivider()
            MonthRow(
                month = month,
                share = (month.spent / maxSpent).toFloat(),
                note = when {
                    month == year.priciestMonth -> "самый дорогой"
                    index == current && currentIsRunning -> "идёт сейчас"
                    else -> null
                },
                onClick = { onOpen(month) }
            )
        }
    }
}

@Composable
private fun MonthRow(month: MonthTotal, share: Float, note: String?, onClick: () -> Unit) {
    val empty = month.isFuture || (month.spent == 0.0 && month.income == 0.0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (empty) Modifier else Modifier.clickable(onClick = onClick))
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(96.dp)) {
            Text(
                text = MONTHS_FULL[month.month],
                style = Kaspi.type.bodyL,
                color = if (empty) Kaspi.colors.ink3 else Kaspi.colors.ink
            )
            note?.let { Text(text = it, style = Kaspi.type.label, color = Kaspi.colors.accent) }
        }
        if (empty) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "—",
                style = Kaspi.type.bodyM,
                color = Kaspi.colors.ink3,
                modifier = Modifier.padding(end = 8.dp)
            )
        } else {
            ShareBar(fraction = share, color = Kaspi.colors.accent, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "−" + formatMoneyWhole(month.spent), style = Kaspi.type.money, color = Kaspi.colors.ink)
                if (month.income > 0) {
                    Text(text = "+" + formatMoneyWhole(month.income), style = Kaspi.type.label, color = Kaspi.colors.pos)
                }
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Открыть операции месяца",
                tint = Kaspi.colors.ink3
            )
        }
    }
}

@Composable
private fun AveragesRow(overall: OverallStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("в день", formatMoneyWhole(overall.perDay), Modifier.weight(1f))
        StatTile("в неделю", formatMoneyWhole(overall.perWeek), Modifier.weight(1f))
        StatTile("в месяц", formatMoneyWhole(overall.perMonth), Modifier.weight(1f))
    }
}

@Composable
private fun WeekdayCard(overall: OverallStats) {
    val averages = overall.weekdayAverages
    val top = averages.indices.maxByOrNull { averages[it] }?.takeIf { averages[it] > 0 }
    AppCard {
        SectionHeader(title = "По дням недели")
        top?.let {
            Text(
                text = "Дороже всего — ${WEEKDAYS_PLURAL[it]}, в среднем ${formatMoneyWhole(averages[it])}",
                style = Kaspi.type.bodyS,
                color = Kaspi.colors.ink2
            )
        }
        Spacer(Modifier.height(16.dp))
        BarChart(bars = averages.mapIndexed { i, v -> BarEntry(WEEKDAYS[i], round(v)) }, height = 96.dp)
    }
}

@Composable
private fun WeeksCard(overall: OverallStats) {
    val weeks = overall.lastWeeks
    AppCard {
        SectionHeader(title = "Последние ${weeks.size} недель")
        Text(
            text = "Последний столбец — текущая неделя, она ещё не закончилась",
            style = Kaspi.type.bodyS,
            color = Kaspi.colors.ink2
        )
        Spacer(Modifier.height(16.dp))
        BarChart(bars = weeks.map { BarEntry("", it.spent) }, height = 72.dp)
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = weeks.firstOrNull()?.let { shortDayFormat.format(it.start) }.orEmpty(),
                style = Kaspi.type.label,
                color = Kaspi.colors.ink2,
                modifier = Modifier.weight(1f)
            )
            Text(text = "эта неделя", style = Kaspi.type.label, color = Kaspi.colors.ink2, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun AllTimeCard(overall: OverallStats) {
    AppCard(contentPadding = 0.dp) {
        overall.priciestDay?.let { day ->
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Самый дорогой день за всё время", style = Kaspi.type.label, color = Kaspi.colors.ink2)
                    Text(text = dayFormat.format(day.start), style = Kaspi.type.bodyM, color = Kaspi.colors.ink)
                }
                Text(text = formatMoneyWhole(day.spent), style = Kaspi.type.amountM, color = Kaspi.colors.ink)
            }
            RowDivider()
        }
        AllTimeLine("Дней без трат", "${overall.daysWithoutSpending} из ${overall.days}", Kaspi.colors.ink)
        RowDivider()
        AllTimeLine("Всего потрачено", "−" + formatMoneyWhole(overall.totalSpent), Kaspi.colors.ink)
        RowDivider()
        AllTimeLine("Всего получено", "+" + formatMoneyWhole(overall.totalIncome), Kaspi.colors.pos)
    }
}

@Composable
private fun AllTimeLine(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(text = label, style = Kaspi.type.bodyM, color = Kaspi.colors.ink, modifier = Modifier.weight(1f))
        Text(text = value, style = Kaspi.type.money, color = color)
    }
}
