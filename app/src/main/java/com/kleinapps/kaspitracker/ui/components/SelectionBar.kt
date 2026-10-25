package com.kleinapps.kaspitracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.util.formatMoney

/**
 * Bar shown while several operations are picked: how many and how much, plus
 * what can be done to all of them at once. Owns the category sheet and the
 * delete confirmation, so screens only pass the four actions in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionBar(
    count: Int,
    total: Double,
    onShared: () -> Unit,
    onOwn: () -> Unit,
    onCategory: (Category) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pickingCategory by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Снять выбор")
                }
                Text(
                    text = "Выбрано: $count",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatMoney(total),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onShared) { Text("Общее") }
                TextButton(onClick = onOwn) { Text("Своё") }
                TextButton(onClick = { pickingCategory = true }) { Text("Категория") }
                TextButton(onClick = { confirmingDelete = true }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (pickingCategory) {
        ModalBottomSheet(onDismissRequest = { pickingCategory = false }) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Категория для $count ${operationsWord(count)}",
                    style = MaterialTheme.typography.titleLarge
                )
                CategoryPickerGrid(
                    selected = null,
                    onSelect = { category ->
                        pickingCategory = false
                        onCategory(category)
                    }
                )
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Удалить операции?") },
            text = { Text("Будет удалено: $count ${operationsWord(count)} на ${formatMoney(total)}.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete()
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Отмена") }
            }
        )
    }
}

/** "1 операция", "3 операции", "7 операций". */
private fun operationsWord(count: Int): String {
    val lastTwo = count % 100
    val last = count % 10
    return when {
        lastTwo in 11..14 -> "операций"
        last == 1 -> "операция"
        last in 2..4 -> "операции"
        else -> "операций"
    }
}
