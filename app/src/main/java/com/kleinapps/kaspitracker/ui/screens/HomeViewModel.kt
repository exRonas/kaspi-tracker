package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.LedgerEntry
import com.kleinapps.kaspitracker.data.Owner
import com.kleinapps.kaspitracker.data.ViewScope
import com.kleinapps.kaspitracker.sync.CoupleStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TransactionRepository
import com.kleinapps.kaspitracker.data.isCountedIncome
import com.kleinapps.kaspitracker.data.isCountedSpending
import com.kleinapps.kaspitracker.service.ListenerWatchdog
import com.kleinapps.kaspitracker.service.NotificationHealth
import com.kleinapps.kaspitracker.util.MonthForecast
import com.kleinapps.kaspitracker.util.forecastMonth
import com.kleinapps.kaspitracker.util.isNotificationAccessGranted
import com.kleinapps.kaspitracker.util.startOfDay
import com.kleinapps.kaspitracker.util.startOfMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DayBar(val label: String, val dayStart: Long, val amount: Double)

data class CategorySum(val category: Category, val amount: Double)

data class HomeUiState(
    val todaySpent: Double = 0.0,
    val todayIncome: Double = 0.0,
    val monthSpent: Double = 0.0,
    val monthIncome: Double = 0.0,
    val last7Days: List<DayBar> = emptyList(),
    val topCategories: List<CategorySum> = emptyList(),
    val recent: List<LedgerEntry> = emptyList(),
    val uncategorizedCount: Int = 0,
    val forecast: MonthForecast? = null,
    /**
     * Spending over the same stretch of last month, so "272 239 ₸" can be read
     * as better or worse than usual instead of just being a big number. Null
     * until there is a previous month to compare against.
     */
    val sameSpanLastMonth: Double? = null,
    /**
     * Hours since the last Kaspi push, set only when that silence is longer
     * than the user's own rhythm explains — i.e. when the listener has most
     * likely been killed and spending is going unrecorded.
     */
    val silentForHours: Int? = null,
    val scope: ViewScope = ViewScope.ME,
    /** "Together" only: how much of the month's shared spending each of us paid. */
    val mySharedPart: Double = 0.0,
    val partnerSharedPart: Double = 0.0
) {
    /** Share more (positive) or less (negative) than the same days last month. */
    val monthTrend: Double?
        get() = sameSpanLastMonth
            ?.takeIf { it > 0 }
            ?.let { (monthSpent - it) / it }
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TransactionRepository.get(app)

    /** Sample of past push times, read once — the rhythm changes slowly. */
    private var notificationHistory: List<Long> = emptyList()

    init {
        viewModelScope.launch {
            notificationHistory = runCatching { repo.notificationTimestamps() }
                .getOrDefault(emptyList())
        }
    }

    // Recomputed on every emission: the app is often left open past midnight (or
    // over a month boundary), and a "now" captured once would keep showing
    // yesterday's "Сегодня" total.
    // Reaches back into last month as well: the hero compares this month with
    // the same stretch of the previous one.
    private val windowStart: Long
        get() = minOf(startOfMonth(previousMonth()), startOfDay(daysAgo = 6))

    private fun previousMonth(now: Long = System.currentTimeMillis()): Long =
        java.util.Calendar.getInstance()
            .apply { timeInMillis = now; add(java.util.Calendar.MONTH, -1) }
            .timeInMillis

    private val scope = CoupleStore.state.map { it.effectiveScope }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<HomeUiState> = combine(
        scope.flatMapLatest { s -> repo.observeLedger(s).map { s to it } },
        repo.observeUncategorized()
    ) { (scope, ledger), uncategorized ->
        val windowStart = windowStart
        val window = ledger.map { it.tx }.filter { it.timestamp >= windowStart }
        val recent = ledger.take(RECENT)
        val now = System.currentTimeMillis()
        val todayStart = startOfDay(now)
        val monthStart = startOfMonth(now)
        val weekStart = startOfDay(now, daysAgo = 6)
        val spendings = window.filter { it.isCountedSpending }
        val incomes = window.filter { it.isCountedIncome }

        val monthSpendings = spendings.filter { it.timestamp >= monthStart }
        // Same number of days into the previous month, so the comparison is
        // like for like rather than a full month against a partial one.
        val previousMonthStart = startOfMonth(previousMonth(now))
        val elapsed = now - monthStart
        val previousSpan = spendings
            .filter { it.timestamp in previousMonthStart until (previousMonthStart + elapsed) }
            .sumOf { it.amount ?: 0.0 }
        val todaySpendings = spendings.filter { it.timestamp >= todayStart }

        HomeUiState(
            todaySpent = todaySpendings.sumOf { it.amount ?: 0.0 },
            todayIncome = incomes.filter { it.timestamp >= todayStart }.sumOf { it.amount ?: 0.0 },
            monthSpent = monthSpendings.sumOf { it.amount ?: 0.0 },
            monthIncome = incomes.filter { it.timestamp >= monthStart }.sumOf { it.amount ?: 0.0 },
            last7Days = buildWeekBars(now, spendings.filter { it.timestamp >= weekStart }),
            topCategories = monthSpendings
                .groupBy { it.category ?: Category.OTHER }
                .map { (category, txs) -> CategorySum(category, txs.sumOf { it.amount ?: 0.0 }) }
                .sortedByDescending { it.amount }
                .take(3),
            recent = recent,
            uncategorizedCount = if (scope == ViewScope.PARTNER) 0 else uncategorized.size,
            scope = scope,
            mySharedPart = ledger.filter { it.owner == Owner.ME && it.tx.isCountedSpending && it.tx.timestamp >= monthStart }
                .sumOf { it.tx.amount ?: 0.0 },
            partnerSharedPart = ledger.filter { it.owner == Owner.PARTNER && it.tx.isCountedSpending && it.tx.timestamp >= monthStart }
                .sumOf { it.tx.amount ?: 0.0 },
            forecast = forecastMonth(monthSpendings.sumOf { it.amount ?: 0.0 }, now),
            // A dead listener only matters for this phone's own data.
            silentForHours = if (scope == ViewScope.PARTNER) null else listenerSilence(now),
            sameSpanLastMonth = previousSpan.takeIf { it > 0 }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun markShared(ids: Set<Long>, shared: Boolean) {
        viewModelScope.launch { repo.setShared(ids, shared) }
    }

    fun recategorize(ids: Set<Long>, category: Category) {
        viewModelScope.launch { repo.setCategory(ids, category) }
    }

    fun deleteMany(ids: Set<Long>) {
        viewModelScope.launch { repo.deleteMany(ids) }
    }

    /**
     * Hours of unexplained silence from the listener, or null while it is
     * healthy. The threshold comes from the user's own push history, so a quiet
     * weekend does not raise a false alarm.
     */
    private fun listenerSilence(now: Long): Int? {
        val context = getApplication<Application>()
        val lastSeen = ListenerWatchdog.lastNotificationAt(context) ?: return null
        if (!isNotificationAccessGranted(context)) return null
        if (!NotificationHealth.isSilent(lastSeen, notificationHistory, now)) return null
        return NotificationHealth.hoursSince(lastSeen, now)
    }

    private fun buildWeekBars(now: Long, weekTx: List<Transaction>): List<DayBar> {
        val labels = arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")
        return (6 downTo 0).map { offset ->
            val dayStart = startOfDay(now, daysAgo = offset)
            val dayEnd = dayStart + DAY_MILLIS
            val sum = weekTx
                .filter { it.timestamp in dayStart until dayEnd }
                .sumOf { it.amount ?: 0.0 }
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = dayStart }
            DayBar(
                label = labels[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1],
                dayStart = dayStart,
                amount = sum
            )
        }
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        const val RECENT = 5
    }
}
