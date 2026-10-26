package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.MerchantKey
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TransactionRepository
import com.kleinapps.kaspitracker.data.isCountedIncome
import com.kleinapps.kaspitracker.data.isCountedSpending
import com.kleinapps.kaspitracker.util.startOfDay
import com.kleinapps.kaspitracker.util.startOfMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import com.kleinapps.kaspitracker.sync.CoupleStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

enum class AnalyticsPeriod(val label: String) {
    WEEK("7 дней"),
    MONTH("Этот месяц"),
    LAST_MONTH("Прошлый месяц"),
    ALL("Всё время")
}

data class MerchantSum(val merchant: String, val amount: Double, val count: Int)

data class AnalyticsUiState(
    val period: AnalyticsPeriod = AnalyticsPeriod.MONTH,
    val spent: Double = 0.0,
    val income: Double = 0.0,
    val byCategory: List<CategorySum> = emptyList(),
    val topMerchants: List<MerchantSum> = emptyList(),
    /** Current month total vs previous month total (for the comparison card). */
    val thisMonth: Double = 0.0,
    val prevMonth: Double = 0.0,
    /** The window the numbers above cover — carried into History on a drill-down. */
    val from: Long = 0L,
    val to: Long = Long.MAX_VALUE
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TransactionRepository.get(app)
    private val periodFlow = MutableStateFlow(AnalyticsPeriod.MONTH)

    private val operations = CoupleStore.state.map { it.effectiveScope }.distinctUntilChanged()
        .flatMapLatest { repo.observeLedger(it) }
        .map { ledger -> ledger.map { it.tx } }

    val state: StateFlow<AnalyticsUiState> = combine(periodFlow, operations) { period, all ->
        build(period, all)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    fun selectPeriod(period: AnalyticsPeriod) {
        periodFlow.value = period
    }

    private fun build(period: AnalyticsPeriod, all: List<Transaction>): AnalyticsUiState {
        val now = System.currentTimeMillis()
        val monthStart = startOfMonth(now)
        val prevMonthStart = startOfMonth(
            Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, -1) }.timeInMillis
        )

        val (from, to) = when (period) {
            AnalyticsPeriod.WEEK -> startOfDay(now, daysAgo = 6) to Long.MAX_VALUE
            AnalyticsPeriod.MONTH -> monthStart to Long.MAX_VALUE
            AnalyticsPeriod.LAST_MONTH -> prevMonthStart to monthStart - 1
            AnalyticsPeriod.ALL -> 0L to Long.MAX_VALUE
        }

        val window = all.filter { it.timestamp in from..to }
        val spendings = window.filter { it.isCountedSpending }

        val monthSpent = all
            .filter { it.isCountedSpending && it.timestamp >= monthStart }
            .sumOf { it.amount ?: 0.0 }
        val prevMonthSpent = all
            .filter { it.isCountedSpending && it.timestamp in prevMonthStart until monthStart }
            .sumOf { it.amount ?: 0.0 }

        return AnalyticsUiState(
            period = period,
            spent = spendings.sumOf { it.amount ?: 0.0 },
            income = window.filter { it.isCountedIncome }.sumOf { it.amount ?: 0.0 },
            byCategory = spendings
                .groupBy { it.category ?: Category.OTHER }
                .map { (category, txs) -> CategorySum(category, txs.sumOf { it.amount ?: 0.0 }) }
                .sortedByDescending { it.amount },
            topMerchants = spendings
                .filter { it.merchant != null }
                // Group on the normalized key, otherwise "SMALL" and "Small" —
                // the same shop — show up as two separate rows.
                .groupBy { MerchantKey.of(it.merchant) ?: it.merchant!!.lowercase() }
                .map { (_, txs) ->
                    MerchantSum(
                        merchant = txs.last().merchant!!,
                        amount = txs.sumOf { it.amount ?: 0.0 },
                        count = txs.size
                    )
                }
                .sortedByDescending { it.amount }
                .take(5),
            thisMonth = monthSpent,
            prevMonth = prevMonthSpent,
            from = from,
            to = to
        )
    }
}
