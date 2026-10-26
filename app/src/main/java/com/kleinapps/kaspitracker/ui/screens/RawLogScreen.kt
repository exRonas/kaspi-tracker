package com.kleinapps.kaspitracker.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TxType
import com.kleinapps.kaspitracker.ui.components.EmptyState
import com.kleinapps.kaspitracker.ui.components.RowDivider
import com.kleinapps.kaspitracker.ui.components.label
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import java.text.SimpleDateFormat
import java.util.Locale

private val stamp = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault())

/** Every Kaspi push the listener caught, as plain monospace — a tool for tuning the parser. */
@Composable
fun RawLogScreen(viewModel: RawLogViewModel = viewModel()) {
    val log by viewModel.log.collectAsStateWithLifecycle()

    if (log.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.Terminal,
                title = "Журнал пуст",
                subtitle = "Как только Kaspi пришлёт уведомление, его текст появится здесь"
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(
                text = "Все перехваченные уведомления Kaspi, включая нераспознанные. " +
                    "Скопируй сюда реальные примеры, чтобы доточить парсер.",
                style = Kaspi.type.bodyS,
                color = Kaspi.colors.ink2,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            RowDivider()
        }
        items(log, key = { it.id }) { tx ->
            LogEntry(tx)
            RowDivider()
        }
    }
}

@Composable
private fun LogEntry(tx: Transaction) {
    val mono = Kaspi.type.mono
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = stamp.format(tx.timestamp), style = mono, color = Kaspi.colors.ink2, modifier = Modifier.weight(1f))
            Text(text = tx.title ?: "", style = mono, color = Kaspi.colors.ink2, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(text = tx.rawText, style = mono, color = Kaspi.colors.ink)
        Spacer(Modifier.height(4.dp))
        if (tx.amount == null || tx.type == TxType.UNKNOWN) {
            Text(text = "× Не распознано", style = mono, color = Kaspi.colors.neg)
        } else {
            val sign = if (tx.type == TxType.TRANSFER_IN) "+" else "-"
            Text(
                text = "→ amount=$sign${tx.amount.toLong()} · type=${tx.type.label()} · merchant=${tx.merchant ?: "—"}",
                style = mono,
                color = Kaspi.colors.pos
            )
        }
    }
}
