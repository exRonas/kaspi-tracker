package com.kleinapps.kaspitracker.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.RuleFolder
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SouthWest
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.data.AccountBalance
import com.kleinapps.kaspitracker.ui.components.AnimatedMoney
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.BarChart
import com.kleinapps.kaspitracker.ui.components.BarEntry
import com.kleinapps.kaspitracker.ui.components.CategoryAvatar
import com.kleinapps.kaspitracker.ui.components.EmptyState
import com.kleinapps.kaspitracker.ui.components.GlowOrbs
import com.kleinapps.kaspitracker.ui.components.IconBadge
import com.kleinapps.kaspitracker.ui.components.NavigationTile
import com.kleinapps.kaspitracker.ui.components.Pill
import com.kleinapps.kaspitracker.ui.components.RankBadge
import com.kleinapps.kaspitracker.ui.components.RingGauge
import com.kleinapps.kaspitracker.ui.components.SectionHeader
import com.kleinapps.kaspitracker.ui.components.Sparkline
import com.kleinapps.kaspitracker.ui.components.ShineSweep
import com.kleinapps.kaspitracker.ui.components.TransactionRow
import com.kleinapps.kaspitracker.ui.components.color
import com.kleinapps.kaspitracker.ui.components.topHighlight
import com.kleinapps.kaspitracker.ui.theme.Cyan
import com.kleinapps.kaspitracker.ui.theme.HeroGradient
import com.kleinapps.kaspitracker.ui.theme.Amber
import com.kleinapps.kaspitracker.ui.theme.Income
import com.kleinapps.kaspitracker.ui.theme.Indigo
import com.kleinapps.kaspitracker.ui.theme.Pink
import com.kleinapps.kaspitracker.ui.theme.Mint
import com.kleinapps.kaspitracker.ui.theme.OnAccent
import com.kleinapps.kaspitracker.ui.theme.Spend
import com.kleinapps.kaspitracker.ui.theme.ThemeController
import com.kleinapps.kaspitracker.ui.theme.ThemeMode
import com.kleinapps.kaspitracker.ui.theme.Violet
import com.kleinapps.kaspitracker.ui.theme.Warning
import com.kleinapps.kaspitracker.ui.theme.VioletDeep
import com.kleinapps.kaspitracker.util.MonthForecast
import com.kleinapps.kaspitracker.util.formatDayHeader
import com.kleinapps.kaspitracker.util.formatFullDate
import com.kleinapps.kaspitracker.util.greeting
import com.kleinapps.kaspitracker.util.formatMoney
import com.kleinapps.kaspitracker.util.formatMoneyWhole
import com.kleinapps.kaspitracker.util.formatTime
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    notificationAccessGranted: Boolean,
    onOpenRawLog: () -> Unit,
    onAddManual: () -> Unit,
    onOpenCategorize: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenBatchImport: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val themeMode by ThemeController.mode.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = greeting(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Kaspi Tracker",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = formatFullDate(System.currentTimeMillis()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // A tinted round button instead of a bare glyph: the only
                    // control in the header deserves to look like one.
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { ThemeController.cycle() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (themeMode) {
                                ThemeMode.LIGHT -> Icons.Filled.LightMode
                                ThemeMode.DARK -> Icons.Filled.DarkMode
                                ThemeMode.SYSTEM -> Icons.Outlined.Contrast
                            },
                            contentDescription = "Сменить тему",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (!notificationAccessGranted) {
                item {
                    PermissionBanner {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    }
                }
            }

            item {
                HeroCard(
                    monthSpent = state.monthSpent,
                    monthIncome = state.monthIncome,
                    todaySpent = state.todaySpent,
                    forecast = state.forecast,
                    trend = state.monthTrend
                )
            }

            state.silentForHours?.let { hours ->
                item {
                    SilentListenerBanner(hours) {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                        )
                    }
                }
            }

            if (state.balances.isNotEmpty()) {
                item { BalanceCard(state.balances, state.balanceHistory) }
            }

            if (state.uncategorizedCount > 0) {
                item {
                    UncategorizedBanner(
                        count = state.uncategorizedCount,
                        onClick = onOpenCategorize
                    )
                }
            }

            item {
                AppCard {
                    SectionHeader(title = "Траты за 7 дней")
                    Spacer(Modifier.height(14.dp))
                    BarChart(bars = state.last7Days.map { BarEntry(it.label, it.amount) })
                }
            }

            if (state.topCategories.isNotEmpty()) {
                item { TopCategoriesCard(state.topCategories) }
            }

            item {
                SectionHeader(
                    title = "Последние операции",
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (state.recent.isEmpty()) {
                item {
                    AppCard {
                        EmptyState(
                            icon = Icons.Filled.ReceiptLong,
                            title = "Пока пусто",
                            subtitle = "Операции появятся, как только придёт уведомление Kaspi " +
                                "или ты добавишь запись вручную.",
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            } else {
                item {
                    AppCard(contentPadding = 6.dp) {
                        state.recent.forEachIndexed { index, tx ->
                            TransactionRow(tx, modifier = Modifier.padding(horizontal = 12.dp))
                            if (index != state.recent.lastIndex) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 68.dp, end = 12.dp)
                                        .height(1.dp)
                                        .background(
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Инструменты",
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    NavigationTile(
                        tint = Indigo,
                        icon = Icons.Filled.UploadFile,
                        title = "Импорт чеков",
                        subtitle = "Загрузить сразу пачку PDF из Загрузок",
                        onClick = onOpenBatchImport
                    )
                    NavigationTile(
                        tint = Pink,
                        icon = Icons.Filled.Autorenew,
                        title = "Подписки",
                        subtitle = "Регулярные платежи и их цена в месяц",
                        onClick = onOpenSubscriptions
                    )
                    NavigationTile(
                        tint = Amber,
                        icon = Icons.Filled.Rule,
                        title = "Правила категорий",
                        subtitle = "Что приложение запомнило про твои магазины",
                        onClick = onOpenRules
                    )
                    NavigationTile(
                        tint = Mint,
                        icon = Icons.Filled.Save,
                        title = "Бэкап и восстановление",
                        subtitle = "Сохранить всё в файл или залить на другой телефон",
                        onClick = onOpenBackup
                    )
                    NavigationTile(
                        tint = Cyan,
                        icon = Icons.Filled.ReceiptLong,
                        title = "Журнал уведомлений",
                        subtitle = "Raw-текст пушей Kaspi для отладки парсера",
                        onClick = onOpenRawLog
                    )
                }
                // Clears the floating button, which otherwise sits on the last tile.
                Spacer(Modifier.height(96.dp))
            }
        }

        // Compact on purpose: an extended button covers a third of the chart
        // card it floats over.
        FloatingActionButton(
            onClick = onAddManual,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = OnAccent,
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Добавить запись")
        }
    }
}

/**
 * The headline card: month total over a gradient, with a ring showing how much
 * of the month is gone. Read together with the projection under it, that
 * answers "am I ahead of my own pace?" without a second screen.
 */
@Composable
private fun HeroCard(
    monthSpent: Double,
    monthIncome: Double,
    todaySpent: Double,
    forecast: MonthForecast?,
    trend: Double?
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Coloured shadow: the card looks lit from within rather than
            // pasted on, and the violet reads through onto the background.
            .shadow(
                elevation = 20.dp,
                shape = shape,
                ambientColor = Violet,
                spotColor = VioletDeep
            )
            .clip(shape)
            .background(Brush.linearGradient(HeroGradient))
    ) {
        GlowOrbs(primary = Color.White, secondary = Cyan)
        ShineSweep()
        Box(modifier = Modifier.matchParentSize().topHighlight(alpha = 0.16f))

        Column(modifier = Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ПОТРАЧЕНО В ЭТОМ МЕСЯЦЕ",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.82f)
                    )
                    Spacer(Modifier.height(6.dp))
                    AnimatedMoney(
                        amount = monthSpent,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )
                    trend?.let {
                        Spacer(Modifier.height(8.dp))
                        TrendPill(it)
                    }
                }
                forecast?.takeIf { it.reliable }?.let { MonthProgressRing(it) }
            }

            forecast?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = if (it.reliable) {
                        "К концу месяца ≈ ${formatMoneyWhole(it.projected)} · " +
                            "${formatMoneyWhole(it.perDay)} в день"
                    } else {
                        "Месяц только начался — прогноз появится через пару дней"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroStat(
                    icon = Icons.Filled.SouthWest,
                    label = "СЕГОДНЯ",
                    value = "− ${formatMoneyWhole(todaySpent)}",
                    modifier = Modifier.weight(1f)
                )
                HeroStat(
                    icon = Icons.Filled.NorthEast,
                    label = "ДОХОД",
                    value = "+ ${formatMoneyWhole(monthIncome)}",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Whether this month is running hotter or cooler than the same days of the
 * previous one — the one line that turns the headline figure into a verdict.
 */
@Composable
private fun TrendPill(trend: Double) {
    val percent = (trend * 100).roundToInt()
    val spendingMore = percent > 0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (spendingMore) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = when {
                percent == 0 -> "как и в прошлом месяце"
                spendingMore -> "+$percent% к прошлому месяцу"
                else -> "$percent% к прошлому месяцу"
            },
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.92f)
        )
    }
}

/** Frosted pill holding one secondary figure on the hero gradient. */
@Composable
private fun HeroStat(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.15f))
            .topHighlight(alpha = 0.12f)
            .padding(horizontal = 14.dp, vertical = 11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.82f)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
    }
}

/** Days left in the month, drawn as the share of the month already gone. */
@Composable
private fun MonthProgressRing(forecast: MonthForecast) {
    RingGauge(
        progress = forecast.dayOfMonth.toFloat() / forecast.daysInMonth,
        modifier = Modifier.size(76.dp),
        diameter = 76.dp
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${forecast.daysLeft}",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Text(
                text = "дн.",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

/** Top three categories of the month, each with a share bar. */
@Composable
private fun TopCategoriesCard(categories: List<CategorySum>) {
    val total = categories.sumOf { it.amount }.coerceAtLeast(1.0)
    AppCard {
        SectionHeader(
            title = "Топ категорий месяца",
            accent = categories.first().category.color,
            trailing = {
                Text(
                    text = formatMoneyWhole(categories.sumOf { it.amount }),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        Spacer(Modifier.height(6.dp))
        val maxAmount = categories.first().amount.coerceAtLeast(1.0)
        categories.forEachIndexed { index, entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box {
                    CategoryAvatar(entry.category, size = 42.dp)
                    // The rank rides on the avatar instead of taking its own
                    // column, so the row keeps one clean left edge.
                    RankBadge(
                        position = index + 1,
                        tint = entry.category.color,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 2.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = entry.category.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.width(8.dp))
                            Pill(
                                text = "${((entry.amount / total) * 100).roundToInt()}%",
                                tint = entry.category.color
                            )
                        }
                        Text(
                            text = formatMoney(entry.amount),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    ShareBar(
                        fraction = (entry.amount / maxAmount).toFloat(),
                        color = entry.category.color
                    )
                }
            }
        }
    }
}

/** Slim progress track used under category names. */
@Composable
private fun ShareBar(fraction: Float, color: Color) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        label = "shareBar"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(6.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color)))
        )
    }
}

/**
 * Last known balance of every Kaspi card, drawn as the card itself rather than
 * as another list row — a plastic-looking panel with a contact pad, so the
 * number checked most often owns a real object on the screen.
 */
@Composable
private fun BalanceCard(
    balances: List<AccountBalance>,
    history: Map<String, List<Double>>
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        balances.forEach { balance ->
            val shape = RoundedCornerShape(24.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(14.dp, shape, ambientColor = Mint, spotColor = Mint)
                    .clip(shape)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF10281F), Color(0xFF123830)))
                    )
            ) {
                GlowOrbs(primary = Mint, secondary = Cyan)
                Box(modifier = Modifier.matchParentSize().topHighlight(alpha = 0.10f))

                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Contact pad: the one detail that makes a rectangle
                        // read as a bank card at a glance.
                        Box(
                            modifier = Modifier
                                .size(width = 30.dp, height = 22.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFFE7C26B), Color(0xFFB98F32))
                                    )
                                )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = balance.account,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Pill(
                            text = "доступно",
                            tint = Mint,
                            contentColor = Mint,
                            background = Mint.copy(alpha = 0.18f)
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(modifier = Modifier.weight(1f)) {
                            AnimatedMoney(
                                amount = balance.balance,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "обновлено ${formatDayHeader(balance.timestamp).lowercase()}, " +
                                    formatTime(balance.timestamp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.62f)
                            )
                        }
                        // How the balance has been moving, drawn small enough
                        // to sit inside the card without competing with it.
                        history[balance.account]?.takeIf { it.size >= 2 }?.let { points ->
                            Sparkline(
                                values = points,
                                color = Mint,
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(34.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Raised when Kaspi has been quiet for longer than the user's own rhythm
 * explains — almost always the OEM battery manager having killed the listener.
 * The app cannot fix that on its own, so it says so instead of pretending the
 * empty history is real.
 */
@Composable
private fun SilentListenerBanner(hours: Int, onOpenSettings: () -> Unit) {
    AppCard(onClick = onOpenSettings, accent = Warning) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconBadge(icon = Icons.Filled.NotificationsOff, tint = Warning)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Операции не приходят ${hours} ч",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Похоже, система усыпила приложение. Проверь доступ к " +
                        "уведомлениям и сними ограничения батареи.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    AppCard(accent = Spend) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconBadge(icon = Icons.Filled.NotificationsActive, tint = Spend)
            Column {
                Text(
                    text = "Нет доступа к уведомлениям",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Без него траты из Kaspi не записываются",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
            Text("Открыть настройки доступа")
        }
    }
}

@Composable
private fun UncategorizedBanner(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Violet.copy(alpha = 0.22f), Violet.copy(alpha = 0.10f))
                )
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconBadge(icon = Icons.Filled.RuleFolder, tint = Violet)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Разобрать операции · $count",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Незнакомый магазин или перевод — уточни, на что",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Violet
            )
        }
    }
}

/** Small centred hint used by other screens in this package. */
@Composable
internal fun EmptyHint(text: String) {
    AppCard {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 6.dp)
        )
    }
}
