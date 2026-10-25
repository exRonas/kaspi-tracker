package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kleinapps.kaspitracker.data.TransactionRepository
import com.kleinapps.kaspitracker.util.OverallStats
import com.kleinapps.kaspitracker.util.Stats
import com.kleinapps.kaspitracker.util.YearStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

data class StatsUiState(
    val years: List<Int> = emptyList(),
    val year: YearStats? = null,
    val overall: OverallStats? = null
)

class StatsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TransactionRepository.get(app)
    private val selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))

    val state: StateFlow<StatsUiState> = combine(
        repo.observeAllWithAmount(),
        selectedYear
    ) { txs, year ->
        // "Now" is read on every emission, so a screen left open over midnight
        // or into a new month moves on with the calendar.
        val now = System.currentTimeMillis()
        StatsUiState(
            years = Stats.years(txs, now),
            year = Stats.year(txs, year, now),
            overall = Stats.overall(txs, now)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun selectYear(year: Int) {
        selectedYear.value = year
    }
}
