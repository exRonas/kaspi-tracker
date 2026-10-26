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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.CategoryPickerGrid
import com.kleinapps.kaspitracker.ui.components.EmptyState
import com.kleinapps.kaspitracker.ui.components.ShareBar
import com.kleinapps.kaspitracker.ui.components.SharedChoice
import com.kleinapps.kaspitracker.ui.components.label
import com.kleinapps.kaspitracker.ui.components.signedAmount
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.util.formatDayHeader
import com.kleinapps.kaspitracker.util.formatTime

/**
 * One-at-a-time queue for operations the classifier couldn't place: pick
 * own/shared and a category — the tap on the category saves and the next one
 * comes in.
 */
@Composable
fun CategorizeScreen(viewModel: CategorizeViewModel = viewModel()) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    // The biggest the queue has been on this visit: the base for the progress line.
    var startedWith by rememberSaveable { mutableIntStateOf(0) }
    var shared by rememberSaveable { mutableStateOf(false) }
    if (queue.size > startedWith) startedWith = queue.size

    if (queue.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.TaskAlt,
                title = "Всё разобрано",
                subtitle = "Новые операции без категории будут появляться здесь"
            )
        }
        return
    }

    val tx = queue.first()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Осталось разобрать: ${queue.size}", style = Kaspi.type.label, color = Kaspi.colors.ink2)
            Spacer(Modifier.width(12.dp))
            ShareBar(
                fraction = 1f - queue.size / startedWith.coerceAtLeast(1).toFloat(),
                color = Kaspi.colors.accent,
                height = 4.dp,
                modifier = Modifier.weight(1f)
            )
        }
        OperationCard(tx)
        Text(text = "На что это было?", style = Kaspi.type.titleL, color = Kaspi.colors.ink)
        SharedChoice(shared = shared, onChange = { shared = it })
        CategoryPickerGrid(
            selected = null,
            onSelect = {
                viewModel.assign(tx, it, shared)
                shared = false
            }
        )
        Text(
            text = "Тап по категории сохраняет и показывает следующую",
            style = Kaspi.type.label,
            color = Kaspi.colors.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OperationCard(tx: Transaction) {
    AppCard(contentPadding = 24.dp) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = tx.signedAmount(), style = Kaspi.type.display, color = Kaspi.colors.ink)
            Spacer(Modifier.height(4.dp))
            Text(
                text = tx.merchant ?: tx.type.label(),
                style = Kaspi.type.titleM,
                color = Kaspi.colors.ink,
                textAlign = TextAlign.Center
            )
            Text(
                text = listOfNotNull("${formatDayHeader(tx.timestamp)}, ${formatTime(tx.timestamp)}", tx.account)
                    .joinToString(" · "),
                style = Kaspi.type.bodyS,
                color = Kaspi.colors.ink2
            )
            if (tx.merchant == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = tx.rawText,
                    style = Kaspi.type.bodyS,
                    color = Kaspi.colors.ink3,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
