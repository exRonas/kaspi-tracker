package com.kleinapps.kaspitracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TxType
import com.kleinapps.kaspitracker.data.isCountedSpending
import com.kleinapps.kaspitracker.ui.components.EditTransactionSheet
import com.kleinapps.kaspitracker.ui.components.SelectionBar
import com.kleinapps.kaspitracker.ui.components.TransactionRow
import com.kleinapps.kaspitracker.ui.components.label
import com.kleinapps.kaspitracker.util.formatMoney

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    initialCategory: Category? = null,
    initialFrom: Long? = null,
    initialTo: Long? = null,
    initialPeriodLabel: String? = null,
    viewModel: HistoryViewModel = viewModel()
) {
    // Arriving from a tap on the analytics screen: show exactly that slice.
    LaunchedEffect(initialCategory, initialFrom, initialTo) {
        if (initialCategory != null || initialFrom != null) {
            viewModel.applyDrillDown(initialCategory, initialFrom, initialTo, initialPeriodLabel)
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Transaction?>(null) }
    // Ids picked with a long press; non-empty means the screen is in pick mode.
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val picking = selected.isNotEmpty()
    BackHandler(enabled = picking) { selected = emptySet() }

    fun toggle(ids: Collection<Long>) {
        selected = if (ids.all { it in selected }) selected - ids.toSet() else selected + ids
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (picking) {
            val picked = state.grouped.values.flatten().filter { it.id in selected }
            SelectionBar(
                count = picked.size,
                total = picked.sumOf { it.amount ?: 0.0 },
                onShared = { viewModel.markShared(selected, true); selected = emptySet() },
                onOwn = { viewModel.markShared(selected, false); selected = emptySet() },
                onCategory = { viewModel.recategorize(selected, it); selected = emptySet() },
                onDelete = { viewModel.deleteMany(selected); selected = emptySet() },
                onClose = { selected = emptySet() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.filters.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text("Поиск: мерчант, сумма, текст…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.filters.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.exportCsv(context) }) {
                Icon(
                    Icons.Filled.IosShare,
                    contentDescription = "Экспорт в CSV",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(true to "Общие", false to "Свои").forEach { (value, label) ->
                FilterChip(
                    selected = state.filters.shared == value,
                    onClick = {
                        viewModel.setSharedFilter(if (state.filters.shared == value) null else value)
                    },
                    label = { Text(label) }
                )
            }
            listOf(TxType.PURCHASE, TxType.TRANSFER_OUT, TxType.TRANSFER_IN).forEach { t ->
                FilterChip(
                    selected = state.filters.type == t,
                    onClick = {
                        viewModel.setType(if (state.filters.type == t) null else t)
                    },
                    label = { Text(t.label()) }
                )
            }
            Category.entries.forEach { c ->
                FilterChip(
                    selected = state.filters.category == c,
                    onClick = {
                        viewModel.setCategory(if (state.filters.category == c) null else c)
                    },
                    label = { Text(c.label) }
                )
            }
            state.accounts.forEach { account ->
                FilterChip(
                    selected = state.filters.account == account,
                    onClick = {
                        viewModel.setAccount(if (state.filters.account == account) null else account)
                    },
                    label = { Text(account) }
                )
            }
        }

        if (state.filters.isActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildString {
                        append("Найдено: ${state.matchCount}")
                        // Arriving from analytics: say which slice is on screen,
                        // otherwise the filtered list looks like lost history.
                        state.filters.periodLabel?.let { append(" · ${it.lowercase()}") }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = viewModel::clearFilters) { Text("Сбросить фильтры") }
            }
        }

        if (state.grouped.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (state.filters.isActive) "Ничего не найдено" else "История пуста",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.grouped.forEach { (header, items) ->
                    item(key = "header_$header") {
                        val dayTotal = items
                            .filter { it.isCountedSpending }
                            .sumOf { it.amount ?: 0.0 }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // In pick mode a tap on the day takes the whole day.
                                .then(
                                    if (picking) Modifier.clickable { toggle(items.map { it.id }) }
                                    else Modifier
                                )
                                .padding(top = 12.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = header,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (dayTotal > 0) {
                                Text(
                                    text = "− ${formatMoney(dayTotal)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item(key = "card_$header") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                items.forEach { tx ->
                                    TransactionRow(
                                        tx = tx,
                                        selected = if (picking) tx.id in selected else null,
                                        modifier = Modifier.combinedClickable(
                                            onClick = {
                                                if (picking) toggle(listOf(tx.id)) else editing = tx
                                            },
                                            onLongClick = { toggle(listOf(tx.id)) }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    editing?.let { tx ->
        EditTransactionSheet(
            tx = tx,
            onSave = { updated ->
                viewModel.save(updated)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}
