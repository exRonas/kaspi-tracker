package com.kleinapps.kaspitracker.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.Owner
import com.kleinapps.kaspitracker.data.ViewScope
import com.kleinapps.kaspitracker.ui.components.ScopeBar
import com.kleinapps.kaspitracker.ui.components.rememberPeople
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TxType
import com.kleinapps.kaspitracker.data.isCountedSpending
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.EditTransactionSheet
import com.kleinapps.kaspitracker.ui.components.EmptyState
import com.kleinapps.kaspitracker.ui.components.FilterPill
import com.kleinapps.kaspitracker.ui.components.KaspiTextField
import com.kleinapps.kaspitracker.ui.components.ScreenHeader
import com.kleinapps.kaspitracker.ui.components.SelectionBar
import com.kleinapps.kaspitracker.ui.components.TransactionRow
import com.kleinapps.kaspitracker.ui.components.icon
import com.kleinapps.kaspitracker.ui.components.label
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.util.formatMoneyWhole
import com.kleinapps.kaspitracker.util.formatWeekday

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    initialCategory: Category? = null,
    initialFrom: Long? = null,
    initialTo: Long? = null,
    initialPeriodLabel: String? = null,
    onAddManual: () -> Unit = {},
    headerActions: @Composable () -> Unit = {},
    viewModel: HistoryViewModel = viewModel()
) {
    // Arriving from a tap on analytics or statistics: show exactly that slice.
    LaunchedEffect(initialCategory, initialFrom, initialTo) {
        if (initialCategory != null || initialFrom != null) {
            viewModel.applyDrillDown(initialCategory, initialFrom, initialTo, initialPeriodLabel)
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val people = rememberPeople()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Transaction?>(null) }
    // Ids picked with a long press; non-empty means the screen is in pick mode.
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val picking = selected.isNotEmpty()
    BackHandler(enabled = picking) { selected = emptySet() }

    fun toggle(ids: Collection<Long>) {
        selected = if (ids.all { it in selected }) selected - ids.toSet() else selected + ids
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                ScreenHeader(kicker = "Все операции", title = "История") {
                    IconButton(onClick = { viewModel.exportCsv(context) }) {
                        Icon(Icons.Outlined.IosShare, contentDescription = "Экспорт в CSV", tint = Kaspi.colors.ink)
                    }
                    headerActions()
                }
            }
            item { ScopeBar() }

            if (picking) {
                item {
                    val picked = state.grouped.values.flatten().map { it.tx }.filter { it.id in selected }
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
            } else {
                item {
                    KaspiTextField(
                        value = state.filters.query,
                        onValueChange = viewModel::setQuery,
                        placeholder = "Поиск: мерчант, сумма, текст…",
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Kaspi.colors.ink2) },
                        trailingIcon = {
                            if (state.filters.query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Очистить")
                                }
                            }
                        }
                    )
                }
                item { Filters(state, viewModel) }
            }

            if (state.filters.isActive) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Найдено: ${state.matchCount}",
                            style = Kaspi.type.label,
                            color = Kaspi.colors.ink2,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Сбросить фильтры",
                            style = Kaspi.type.label,
                            color = Kaspi.colors.accent,
                            modifier = Modifier
                                .clickable(onClick = viewModel::clearFilters)
                                .padding(8.dp)
                        )
                    }
                }
            }

            if (state.grouped.isEmpty()) {
                item {
                    if (state.filters.isActive) {
                        EmptyState(
                            icon = Icons.Outlined.SearchOff,
                            title = "Ничего не найдено",
                            subtitle = "Попробуй другой запрос или сбрось фильтры",
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        EmptyState(
                            icon = Icons.Outlined.ReceiptLong,
                            title = "История пуста",
                            subtitle = "Операции появятся, как только придёт уведомление Kaspi",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            state.grouped.forEach { (header, items) ->
                item(key = "header_$header") {
                    val dayTotal = items.filter { it.tx.isCountedSpending }.sumOf { it.tx.amount ?: 0.0 }
                    val title = if (header == "Сегодня" || header == "Вчера") header
                    else "$header · ${formatWeekday(items.first().tx.timestamp)}"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // In pick mode a tap on the day takes the whole day.
                            .then(
                                if (picking) Modifier.clickable {
                                    toggle(items.filter { it.owner == Owner.ME }.map { it.tx.id })
                                } else Modifier
                            )
                            .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = title, style = Kaspi.type.bodyS, color = Kaspi.colors.ink2, modifier = Modifier.weight(1f))
                        if (dayTotal > 0) {
                            Text(text = "− ${formatMoneyWhole(dayTotal)}", style = Kaspi.type.bodyS, color = Kaspi.colors.ink2)
                        }
                    }
                }
                item(key = "card_$header") {
                    AppCard(contentPadding = 0.dp) {
                        items.forEach { entry ->
                            val tx = entry.tx
                            // The partner's rows are for reading: no editing, no picking.
                            val own = entry.owner == Owner.ME
                            TransactionRow(
                                tx = tx,
                                selected = if (picking && own) tx.id in selected else null,
                                owner = if (state.scope == ViewScope.TOGETHER) people.mark(entry.owner) else null,
                                modifier = Modifier
                                    .then(
                                        if (own) Modifier.combinedClickable(
                                            onClick = { if (picking) toggle(listOf(tx.id)) else editing = tx },
                                            onLongClick = { toggle(listOf(tx.id)) }
                                        ) else Modifier
                                    )
                                    .padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }
            if (state.grouped.isNotEmpty()) {
                item {
                    Text(
                        text = "Долгое нажатие — выбрать несколько операций",
                        style = Kaspi.type.label,
                        color = Kaspi.colors.ink3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }

        if (!picking && state.scope != ViewScope.PARTNER) {
            ExtendedFloatingActionButton(
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

    editing?.let { tx ->
        EditTransactionSheet(
            tx = tx,
            onSave = { updated ->
                viewModel.save(updated)
                editing = null
            },
            onDelete = {
                viewModel.deleteMany(setOf(it.id))
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun Filters(state: HistoryUiState, viewModel: HistoryViewModel) {
    val f = state.filters
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        f.periodLabel?.let { period ->
            FilterPill(label = period, selected = true, closable = true, onClick = viewModel::clearPeriod)
        }
        listOf(TxType.PURCHASE, TxType.TRANSFER_OUT, TxType.TRANSFER_IN).forEach { t ->
            FilterPill(label = t.label(), selected = f.type == t, onClick = {
                viewModel.setType(if (f.type == t) null else t)
            })
        }
        FilterPill(label = "Общее", selected = f.shared == true, onClick = {
            viewModel.setSharedFilter(if (f.shared == true) null else true)
        })
        FilterPill(label = "Своё", selected = f.shared == false, onClick = {
            viewModel.setSharedFilter(if (f.shared == false) null else false)
        })
        Category.entries.forEach { c ->
            FilterPill(label = c.label, selected = f.category == c, leadingIcon = c.icon, onClick = {
                viewModel.setCategory(if (f.category == c) null else c)
            })
        }
        state.accounts.forEach { account ->
            FilterPill(label = account, selected = f.account == account, onClick = {
                viewModel.setAccount(if (f.account == account) null else account)
            })
        }
    }
    Spacer(Modifier.height(4.dp))
}
