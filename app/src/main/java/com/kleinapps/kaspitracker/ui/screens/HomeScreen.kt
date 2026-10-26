package com.kleinapps.kaspitracker.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.Color
import com.kleinapps.kaspitracker.data.Owner
import com.kleinapps.kaspitracker.data.ViewScope
import com.kleinapps.kaspitracker.sync.CoupleStore
import com.kleinapps.kaspitracker.ui.components.AnimatedMoney
import com.kleinapps.kaspitracker.ui.components.PartnerViewNote
import com.kleinapps.kaspitracker.ui.components.People
import com.kleinapps.kaspitracker.ui.components.PersonAvatar
import com.kleinapps.kaspitracker.ui.components.ScopeBar
import com.kleinapps.kaspitracker.ui.components.rememberPeople
import com.kleinapps.kaspitracker.ui.components.syncAgo
import androidx.compose.material.icons.outlined.FavoriteBorder
import com.kleinapps.kaspitracker.util.monthName
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.Banner
import com.kleinapps.kaspitracker.ui.components.BarChart
import com.kleinapps.kaspitracker.ui.components.BarEntry
import com.kleinapps.kaspitracker.ui.components.CategoryAvatar
import com.kleinapps.kaspitracker.ui.components.DayRing
import com.kleinapps.kaspitracker.ui.components.EmptyState
import com.kleinapps.kaspitracker.ui.components.LinkRow
import com.kleinapps.kaspitracker.ui.components.Pill
import com.kleinapps.kaspitracker.ui.components.RowDivider
import com.kleinapps.kaspitracker.ui.components.ScreenHeader
import com.kleinapps.kaspitracker.ui.components.SectionHeader
import com.kleinapps.kaspitracker.ui.components.SelectionBar
import com.kleinapps.kaspitracker.ui.components.ShareBar
import com.kleinapps.kaspitracker.ui.components.ToolTile
import com.kleinapps.kaspitracker.ui.components.TransactionRow
import com.kleinapps.kaspitracker.ui.components.color
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.util.MonthForecast
import com.kleinapps.kaspitracker.util.formatFullDate
import com.kleinapps.kaspitracker.util.formatMoneyWhole
import com.kleinapps.kaspitracker.util.greeting
import com.kleinapps.kaspitracker.util.monthIn
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    notificationAccessGranted: Boolean,
    onOpenRawLog: () -> Unit,
    onAddManual: () -> Unit,
    onOpenCategorize: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenBatchImport: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPair: () -> Unit,
    headerActions: @Composable () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val couple by CoupleStore.state.collectAsStateWithLifecycle()
    val people = rememberPeople()
    val scope = state.scope
    val readOnly = scope == ViewScope.PARTNER
    val context = LocalContext.current
    // Recent operations picked with a long press; non-empty means pick mode.
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val picking = selected.isNotEmpty()
    BackHandler(enabled = picking) { selected = emptySet() }
    val openAccessSettings = {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeader(
                    kicker = formatFullDate(System.currentTimeMillis()),
                    title = when (scope) {
                        ViewScope.ME -> greeting() + couple.myName.takeIf { it.isNotBlank() }?.let { ",\n$it" }.orEmpty()
                        ViewScope.PARTNER -> "${monthName()} · ${people.partnerName}"
                        ViewScope.TOGETHER -> "Наш ${monthName().lowercase()}"
                    }
                ) { headerActions() }
            }
            item { ScopeBar() }
            if (readOnly) item { PartnerViewNote(couple) }

            if (!notificationAccessGranted && !readOnly) {
                item {
                    Banner(
                        icon = Icons.Outlined.NotificationsOff,
                        title = "Нет доступа к уведомлениям",
                        text = "Без него траты из Kaspi не записываются",
                        actionLabel = "Открыть настройки доступа",
                        onAction = openAccessSettings
                    )
                }
            }
            state.silentForHours?.let { hours ->
                item {
                    Banner(
                        icon = Icons.Outlined.Bedtime,
                        title = "Операции не приходят $hours ч",
                        text = "Похоже, система усыпила приложение. Проверь доступ к уведомлениям " +
                            "и сними ограничения батареи",
                        actionLabel = "Проверить",
                        onAction = openAccessSettings
                    )
                }
            }

            item {
                HeroCard(
                    label = when (scope) {
                        ViewScope.ME -> "Потрачено в ${monthIn()}"
                        ViewScope.PARTNER -> "${people.partnerName} · потрачено в ${monthIn()}"
                        ViewScope.TOGETHER -> "Общие траты в ${monthIn()}"
                    },
                    split = if (scope == ViewScope.TOGETHER) {
                        { ContributionSplit(state.mySharedPart, state.partnerSharedPart, people) }
                    } else null,
                    monthSpent = state.monthSpent,
                    monthIncome = state.monthIncome,
                    todaySpent = state.todaySpent,
                    forecast = state.forecast,
                    trend = state.monthTrend
                )
            }

            if (state.uncategorizedCount > 0) {
                item { ReviewBanner(state.uncategorizedCount, onOpenCategorize) }
            }

            item { WeekCard(state.last7Days) }

            if (state.topCategories.isNotEmpty()) {
                item { TopCategoriesCard(state.topCategories, state.monthSpent) }
            }

            if (picking) {
                item {
                    val picked = state.recent.map { it.tx }.filter { it.id in selected }
                    SelectionBar(
                        count = picked.size,
                        total = picked.sumOf { it.amount ?: 0.0 },
                        onShared = { viewModel.markShared(selected, true); selected = emptySet() },
                        onOwn = { viewModel.markShared(selected, false); selected = emptySet() },
                        onCategory = { viewModel.recategorize(selected, it); selected = emptySet() },
                        onDelete = { viewModel.deleteMany(selected); selected = emptySet() },
                        onClose = { selected = emptySet() }
                    )
                }
            }
            item {
                AppCard(contentPadding = 0.dp) {
                    SectionHeader(
                        title = "Последние операции",
                        actionLabel = "Вся история",
                        onAction = onOpenHistory,
                        modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 4.dp)
                    )
                    if (state.recent.isEmpty()) {
                        EmptyState(
                            icon = Icons.Outlined.ReceiptLong,
                            title = "Пока пусто",
                            subtitle = "Операции появятся, как только придёт уведомление Kaspi " +
                                "или ты добавишь запись вручную",
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                    state.recent.forEach { entry ->
                        val tx = entry.tx
                        // Only own rows can be picked; the partner's are read-only here.
                        val own = entry.owner == Owner.ME
                        TransactionRow(
                            tx = tx,
                            selected = if (picking && own) tx.id in selected else null,
                            owner = if (scope == ViewScope.TOGETHER) people.mark(entry.owner) else null,
                            modifier = Modifier
                                // Long press starts picking; while picking a tap
                                // toggles. Outside pick mode rows stay inert here.
                                .then(
                                    if (own) Modifier.combinedClickable(
                                        onClick = { if (picking) selected = selected.toggle(tx.id) },
                                        onLongClick = { selected = selected.toggle(tx.id) }
                                    ) else Modifier
                                )
                                .padding(horizontal = 16.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (!readOnly) item {
                Text(
                    text = "Инструменты",
                    style = Kaspi.type.titleM,
                    color = Kaspi.colors.ink,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp)
                )
            }
            if (!readOnly) item {
                ToolGrid(
                    tiles = listOf(
                        Tool(
                            Icons.Outlined.FavoriteBorder,
                            "Пара",
                            if (couple.paired) "Вы связаны · ${people.partnerName} · ${syncAgo(couple.lastSyncAt)}"
                            else "Связать телефоны и смотреть траты вдвоём",
                            onOpenPair
                        ),
                        Tool(Icons.Outlined.AddCircleOutline, "Добавить запись", "Наличные и всё, что без пуша", onAddManual),
                        Tool(Icons.Outlined.PictureAsPdf, "Импорт чеков", "Загрузить сразу пачку PDF из Загрузок", onOpenBatchImport),
                        Tool(Icons.Outlined.Rule, "Правила категорий", "Что приложение запомнило о магазинах", onOpenRules),
                        Tool(Icons.Outlined.CloudUpload, "Бэкап", "Сохранить всё в файл или перенести", onOpenBackup)
                    )
                )
            }
            if (!readOnly) item {
                AppCard(contentPadding = 0.dp) {
                    LinkRow(
                        icon = Icons.Outlined.Terminal,
                        title = "Журнал уведомлений",
                        onClick = onOpenRawLog
                    )
                }
            }
        }

        if (!readOnly) ExtendedFloatingActionButton(
            onClick = onAddManual,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            containerColor = Kaspi.colors.accent,
            contentColor = Kaspi.colors.onAcc,
            shape = RoundedCornerShape(16.dp),
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("Запись", style = Kaspi.type.bodyL) }
        )
    }
}

/**
 * The month so far: total, pace against last month, a ring of the month's
 * days, the projection, and today / income underneath.
 */
@Composable
private fun HeroCard(
    label: String,
    split: (@Composable () -> Unit)?,
    monthSpent: Double,
    monthIncome: Double,
    todaySpent: Double,
    forecast: MonthForecast?,
    trend: Double?
) {
    AppCard(contentPadding = 20.dp) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = Kaspi.type.bodyM, color = Kaspi.colors.ink2)
                Spacer(Modifier.height(4.dp))
                AnimatedMoney(amount = monthSpent, style = Kaspi.type.display)
                Spacer(Modifier.height(8.dp))
                TrendPill(trend)
            }
            if (forecast != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DayRing(
                        progress = forecast.dayOfMonth / forecast.daysInMonth.toFloat(),
                        label = forecast.dayOfMonth.toString()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${forecast.dayOfMonth} из ${forecast.daysInMonth} дн.",
                        style = Kaspi.type.label,
                        color = Kaspi.colors.ink2
                    )
                }
            }
        }
        if (forecast != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (forecast.reliable) {
                    "К концу месяца ≈ ${formatMoneyWhole(forecast.projected)} · " +
                        "${formatMoneyWhole(forecast.perDay)} в день"
                } else {
                    "Месяц только начался — прогноз появится через пару дней"
                },
                style = Kaspi.type.bodyM,
                color = Kaspi.colors.ink2
            )
        }
        Spacer(Modifier.height(16.dp))
        RowDivider()
        Spacer(Modifier.height(12.dp))
        if (split != null) {
            split()
            return@AppCard
        }
        Row {
            HeroStat("СЕГОДНЯ", "−" + formatMoneyWhole(todaySpent), Kaspi.colors.ink, Modifier.weight(1f))
            HeroStat("ДОХОД", "+" + formatMoneyWhole(monthIncome), Kaspi.colors.pos, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TrendPill(trend: Double?) {
    val percent = trend?.let { (it * 100).roundToInt() }
    val (icon, text) = when {
        percent == null -> return
        percent > 0 -> Icons.AutoMirrored.Rounded.TrendingUp to "+$percent% к прошлому месяцу"
        percent < 0 -> Icons.AutoMirrored.Rounded.TrendingDown to "−${abs(percent)}% к прошлому месяцу"
        else -> Icons.AutoMirrored.Rounded.TrendingFlat to "как и в прошлом месяце"
    }
    Pill(
        text = text,
        tint = Kaspi.colors.ink,
        icon = { Icon(icon, contentDescription = null, tint = Kaspi.colors.accent, modifier = Modifier.size(16.dp)) },
        modifier = Modifier.padding(vertical = 2.dp)
    )
}

@Composable
private fun HeroStat(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(text = label, style = Kaspi.type.overline, color = Kaspi.colors.ink2)
        Text(text = value, style = Kaspi.type.amountM, color = color, maxLines = 1)
    }
}

/** "Кто сколько внёс": the month's shared spending split between the two of us. */
@Composable
private fun ContributionSplit(mine: Double, partners: Double, people: People) {
    val total = mine + partners
    Text(text = "КТО СКОЛЬКО ВНЁС", style = Kaspi.type.overline, color = Kaspi.colors.ink2)
    Spacer(Modifier.height(8.dp))
    if (total <= 0) {
        Text(text = "Общих трат в этом месяце ещё нет", style = Kaspi.type.bodyS, color = Kaspi.colors.ink2)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (mine > 0) ShareBar(1f, people.myColor, Modifier.weight(mine.toFloat()), height = 8.dp)
        if (partners > 0) ShareBar(1f, people.partnerColor, Modifier.weight(partners.toFloat()), height = 8.dp)
    }
    Spacer(Modifier.height(12.dp))
    Row {
        ContributionPart(people.myInitial, people.myColor, people.myName, mine, total, Modifier.weight(1f))
        ContributionPart(people.partnerInitial, people.partnerColor, people.partnerName, partners, total, Modifier.weight(1f))
    }
}

@Composable
private fun ContributionPart(initial: String, color: Color, name: String, amount: Double, total: Double, modifier: Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.Top) {
        PersonAvatar(initial, color, size = 22.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = "$name · ${(amount / total * 100).roundToInt()}%",
                style = Kaspi.type.label,
                color = Kaspi.colors.ink2,
                maxLines = 1
            )
            Text(text = formatMoneyWhole(amount), style = Kaspi.type.amountM, color = Kaspi.colors.ink)
        }
    }
}

@Composable
private fun ReviewBanner(count: Int, onClick: () -> Unit) {
    AppCard(onClick = onClick, color = Kaspi.colors.accentSoft) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Kaspi.colors.accent),
                contentAlignment = Alignment.Center
            ) {
                Text(text = count.toString(), style = Kaspi.type.bodyL, color = Kaspi.colors.onAcc)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Разобрать операции", style = Kaspi.type.titleM.copy(fontSize = Kaspi.type.bodyL.fontSize), color = Kaspi.colors.ink)
                Text(
                    text = "Незнакомый магазин или перевод — уточни, на что",
                    style = Kaspi.type.bodyS,
                    color = Kaspi.colors.ink2
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Kaspi.colors.accent)
        }
    }
}

@Composable
private fun WeekCard(days: List<DayBar>) {
    val peak = days.withIndex().maxByOrNull { it.value.amount }?.takeIf { it.value.amount > 0 }
    AppCard {
        SectionHeader(title = "Траты за 7 дней")
        Spacer(Modifier.height(16.dp))
        if (peak == null) {
            Text(text = "За неделю трат не было", style = Kaspi.type.bodyM, color = Kaspi.colors.ink2)
        } else {
            BarChart(
                bars = days.map { BarEntry(it.label, it.amount, detail = formatFullDate(it.dayStart)) },
                showAverage = true,
                emphasizedLabel = days.lastIndex
            )
        }
    }
}

@Composable
private fun TopCategoriesCard(categories: List<CategorySum>, monthTotal: Double) {
    AppCard {
        SectionHeader(title = "Топ категорий месяца")
        Spacer(Modifier.height(8.dp))
        categories.forEach { entry ->
            val share = if (monthTotal > 0) (entry.amount / monthTotal).toFloat() else 0f
            Row(
                modifier = Modifier.padding(vertical = 8.dp),
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
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(text = formatMoneyWhole(entry.amount), style = Kaspi.type.money, color = Kaspi.colors.ink)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShareBar(fraction = share, color = entry.category.color, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "${(share * 100).roundToInt()}%",
                            style = Kaspi.type.label,
                            color = Kaspi.colors.ink2,
                            modifier = Modifier.width(36.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

private data class Tool(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit
)

@Composable
private fun ToolGrid(tiles: List<Tool>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tool ->
                    ToolTile(tool.icon, tool.title, tool.subtitle, tool.onClick, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id
