package com.kleinapps.kaspitracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.ui.components.CategoryPickerGrid
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.EmptyState
import com.kleinapps.kaspitracker.ui.components.label
import com.kleinapps.kaspitracker.ui.theme.Income
import com.kleinapps.kaspitracker.util.formatDayHeader
import com.kleinapps.kaspitracker.util.formatMoney
import com.kleinapps.kaspitracker.util.formatTime

/**
 * One-at-a-time queue for transactions the classifier couldn't place: pick a
 * category, it saves and the next item slides in automatically.
 */
@Composable
fun CategorizeScreen(viewModel: CategorizeViewModel = viewModel()) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val total = queue.size

    if (queue.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Filled.CheckCircle,
                title = "Всё разобрано",
                subtitle = "Новые операции без категории будут появляться здесь",
                tint = Income
            )
        }
        return
    }

    val tx = queue.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Осталось разобрать: $total",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
        )

        TransactionSummaryCard(tx)

        Spacer(Modifier.height(20.dp))
        Text(
            text = "На что это было?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(10.dp))
        CategoryPickerGrid(
            selected = tx.category,
            onSelect = { viewModel.assign(tx, it) }
        )
    }
}

@Composable
private fun TransactionSummaryCard(tx: Transaction) {
    AppCard {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tx.merchant ?: tx.type.label(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (tx.amount != null) "− ${formatMoney(tx.amount, tx.currency)}" else "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(tx.type.label())
                    append(" · ")
                    append(formatDayHeader(tx.timestamp))
                    append(", ")
                    append(formatTime(tx.timestamp))
                    tx.account?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (tx.merchant == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = tx.rawText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4
                )
            }
        }
    }
}
