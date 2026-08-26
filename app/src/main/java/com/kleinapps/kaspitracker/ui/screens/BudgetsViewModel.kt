package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kleinapps.kaspitracker.data.Budget
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.TransactionRepository
import com.kleinapps.kaspitracker.util.MonthForecast
import com.kleinapps.kaspitracker.util.forecastMonth
import com.kleinapps.kaspitracker.util.startOfMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BudgetRow(
    val category: Category,
    val limit: Double?,
    val spentThisMonth: Double,
    /** Where this category lands by month end at the current rate. */
    val forecast: MonthForecast? = null
) {
    val progress: Float
        get() = if (limit == null || limit <= 0) 0f else (spentThisMonth / limit).toFloat()
    val isOver: Boolean
        get() = limit != null && spentThisMonth > limit

    /** Set when the limit holds today but the trend breaks it before month end. */
    val projectedOvershoot: Double?
        get() = if (isOver) null else forecast?.takeIf { it.reliable }?.overshoot(limit)
}

class BudgetsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TransactionRepository.get(app)

    val rows: StateFlow<List<BudgetRow>> = combine(
        repo.observeBudgets(),
        // Query from the start of the *previous* month and cut to the current one
        // below, so budgets roll over on the 1st without restarting the app.
        repo.observeBetween(startOfMonth(previousMonth()), Long.MAX_VALUE)
    ) { budgets, monthTx ->
        val monthStart = startOfMonth()
        val spentByCategory = monthTx
            .filter { it.amount != null && it.type.isSpending && it.timestamp >= monthStart }
            .groupBy { it.category ?: Category.OTHER }
            .mapValues { (_, txs) -> txs.sumOf { it.amount ?: 0.0 } }

        Category.budgetable.map { category ->
            BudgetRow(
                category = category,
                limit = budgets.firstOrNull { it.category == category }?.monthlyLimit,
                spentThisMonth = spentByCategory[category] ?: 0.0,
                forecast = forecastMonth(spentByCategory[category] ?: 0.0)
            )
        }.sortedWith(
            // Categories with a budget first (by usage desc), then the rest by spend.
            compareByDescending<BudgetRow> { it.limit != null }
                .thenByDescending { it.progress }
                .thenByDescending { it.spentThisMonth }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun previousMonth(): Long =
        java.util.Calendar.getInstance()
            .apply { add(java.util.Calendar.MONTH, -1) }
            .timeInMillis

    fun setLimit(category: Category, limit: Double?) {
        viewModelScope.launch {
            if (limit == null || limit <= 0) {
                repo.removeBudget(Budget(category, 0.0))
            } else {
                repo.setBudget(Budget(category, limit))
            }
        }
    }
}
