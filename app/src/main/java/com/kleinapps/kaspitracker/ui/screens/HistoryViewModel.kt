package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TransactionRepository
import com.kleinapps.kaspitracker.data.TxType
import com.kleinapps.kaspitracker.util.formatDayHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

data class HistoryFilters(
    val query: String = "",
    val type: TxType? = null,
    val category: Category? = null,
    val account: String? = null,
    /** true — only shared operations, false — only own ones, null — both. */
    val shared: Boolean? = null,
    /** Time window, set when arriving from a period on the analytics screen. */
    val from: Long? = null,
    val to: Long? = null,
    /** Human-readable name of that window ("Этот месяц"), for the filter chip. */
    val periodLabel: String? = null
) {
    val isActive: Boolean
        get() = query.isNotBlank() || type != null || category != null ||
            account != null || shared != null || from != null || to != null
}

data class HistoryUiState(
    val grouped: Map<String, List<Transaction>> = emptyMap(),
    val filters: HistoryFilters = HistoryFilters(),
    val accounts: List<String> = emptyList(),
    val matchCount: Int = 0
)

class HistoryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TransactionRepository.get(app)
    private val filtersFlow = MutableStateFlow(HistoryFilters())

    val state: StateFlow<HistoryUiState> = combine(
        repo.observeAllWithAmount(),
        repo.observeAccounts(),
        filtersFlow
    ) { all, accounts, filters ->
        val filtered = all.filter { tx -> matches(tx, filters) }
        HistoryUiState(
            grouped = filtered.groupBy { formatDayHeader(it.timestamp) },
            filters = filters,
            accounts = accounts,
            matchCount = filtered.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private fun matches(tx: Transaction, f: HistoryFilters): Boolean {
        if (f.from != null && tx.timestamp < f.from) return false
        if (f.to != null && tx.timestamp > f.to) return false
        if (f.type != null && tx.type != f.type) return false
        if (f.category != null && (tx.category ?: Category.OTHER) != f.category) return false
        if (f.account != null && tx.account != f.account) return false
        if (f.shared != null && tx.shared != f.shared) return false
        if (f.query.isNotBlank()) {
            val q = f.query.trim().lowercase()
            val haystack = listOfNotNull(
                tx.merchant, tx.rawText, tx.amount?.toString(), tx.account
            ).joinToString(" ").lowercase()
            if (!haystack.contains(q)) return false
        }
        return true
    }

    fun setQuery(query: String) = update { it.copy(query = query) }
    fun setType(type: TxType?) = update { it.copy(type = type) }
    fun setCategory(category: Category?) = update { it.copy(category = category) }
    fun setAccount(account: String?) = update { it.copy(account = account) }
    fun setSharedFilter(shared: Boolean?) = update { it.copy(shared = shared) }

    /** Applies the category + period the user tapped on the analytics screen. */
    fun applyDrillDown(category: Category?, from: Long?, to: Long?, periodLabel: String?) {
        filtersFlow.value = HistoryFilters(
            category = category,
            from = from,
            to = to,
            periodLabel = periodLabel
        )
    }

    fun clearPeriod() = update { it.copy(from = null, to = null, periodLabel = null) }
    fun clearFilters() {
        filtersFlow.value = HistoryFilters()
    }

    private fun update(block: (HistoryFilters) -> HistoryFilters) {
        filtersFlow.value = block(filtersFlow.value)
    }

    fun save(transaction: Transaction) {
        viewModelScope.launch { repo.update(transaction) }
    }

    fun markShared(ids: Set<Long>, shared: Boolean) {
        viewModelScope.launch { repo.setShared(ids, shared) }
    }

    fun recategorize(ids: Set<Long>, category: Category) {
        viewModelScope.launch { repo.setCategory(ids, category) }
    }

    fun deleteMany(ids: Set<Long>) {
        viewModelScope.launch { repo.deleteMany(ids) }
    }

    /** Exports the currently filtered rows to CSV and opens the system share sheet. */
    fun exportCsv(context: Context) {
        val rows = state.value.grouped.values.flatten()
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { writeCsv(context, rows) }
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Экспорт операций"))
        }
    }

    private fun writeCsv(context: Context, rows: List<Transaction>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(System.currentTimeMillis())
        val file = File(dir, "kaspi_tracker_$stamp.csv")
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US)
        file.bufferedWriter(Charsets.UTF_8).use { out ->
            // BOM so Excel opens UTF-8 Cyrillic correctly.
            out.write("﻿")
            out.write("Дата;Тип;Категория;Мерчант;Счёт;Сумма;Валюта;Источник\n")
            rows.sortedByDescending { it.timestamp }.forEach { tx ->
                out.write(
                    listOf(
                        dateFormat.format(tx.timestamp),
                        tx.type.name,
                        (tx.category ?: Category.OTHER).label,
                        csvEscape(tx.merchant ?: ""),
                        csvEscape(tx.account ?: ""),
                        tx.amount?.toString() ?: "",
                        tx.currency,
                        tx.source.name
                    ).joinToString(";") + "\n"
                )
            }
        }
        return file
    }

    private fun csvEscape(value: String): String =
        if (value.contains(';') || value.contains('"') || value.contains('\n')) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else value
}
