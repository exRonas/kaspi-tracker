package com.kleinapps.kaspitracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import com.kleinapps.kaspitracker.ui.components.Pill
import com.kleinapps.kaspitracker.ui.components.RingGauge
import com.kleinapps.kaspitracker.ui.theme.Amber
import com.kleinapps.kaspitracker.ui.theme.Warning
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.ui.components.CategoryAvatar
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.color
import com.kleinapps.kaspitracker.ui.theme.Spend
import com.kleinapps.kaspitracker.util.formatMoney
import com.kleinapps.kaspitracker.util.formatMoneyWhole

@Composable
fun BudgetsScreen(viewModel: BudgetsViewModel = viewModel()) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()
    var editingRow by remember { mutableStateOf<BudgetRow?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { BudgetsSummary(rows) }

        val heaviest = rows.maxOfOrNull { it.spentThisMonth } ?: 0.0
        val monthTotal = rows.sumOf { it.spentThisMonth }
        items(rows, key = { it.category.name }) { row ->
            val accent = when {
                row.isOver -> Spend
                row.limit != null && row.progress >= 0.8f -> Warning
                row.limit != null -> row.category.color
                else -> null
            }
            AppCard(onClick = { editingRow = row }, contentPadding = 16.dp, accent = accent) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // A limit turns the avatar into a gauge: the ring is the
                    // fastest read of "how much of it is gone".
                    if (row.limit != null) {
                        RingGauge(
                            progress = row.progress,
                            modifier = Modifier.size(52.dp),
                            diameter = 52.dp,
                            stroke = 5.dp,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            progressColor = accent ?: row.category.color
                        ) {
                            CategoryAvatar(row.category, size = 32.dp)
                        }
                    } else {
                        CategoryAvatar(row.category, size = 44.dp)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = row.category.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(8.dp))
                            when {
                                row.isOver -> Pill(text = "перерасход", tint = Spend)
                                row.limit != null && row.progress >= 0.8f ->
                                    Pill(text = "на исходе", tint = Warning)
                                row.limit != null ->
                                    Pill(
                                        text = "${(row.progress * 100).roundToInt()}%",
                                        tint = row.category.color
                                    )
                                // No pill without a limit: repeated on every
                                // row it made the whole list look identical.
                                else -> Text(
                                    text = formatMoneyWhole(row.spentThisMonth),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        if (row.limit != null) {
                            Text(
                                text = "${formatMoneyWhole(row.spentThisMonth)} из ${formatMoneyWhole(row.limit)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // Share of the heaviest category, so a list with no
                            // limits set still has a readable shape.
                            // Share of the month rather than the same call to
                            // action on every row — the hint lives in the card
                            // above, and this actually differs line to line.
                            Text(
                                text = if (monthTotal > 0) {
                                    "${((row.spentThisMonth / monthTotal) * 100).roundToInt()}% трат месяца"
                                } else {
                                    "Ещё ничего не потрачено"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(7.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(
                                            if (heaviest > 0) {
                                                (row.spentThisMonth / heaviest)
                                                    .toFloat()
                                                    .coerceIn(0.02f, 1f)
                                            } else {
                                                0.02f
                                            }
                                        )
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    row.category.color.copy(alpha = 0.65f),
                                                    row.category.color
                                                )
                                            )
                                        )
                                )
                            }
                        }
                        row.limit?.let { limit ->
                            val left = limit - row.spentThisMonth
                            Text(
                                text = if (left >= 0) {
                                    "Осталось ${formatMoneyWhole(left)}"
                                } else {
                                    "Сверх лимита ${formatMoneyWhole(-left)}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (left >= 0) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    Spend
                                }
                            )
                        }
                    }
                }

                // Warn before the limit is actually broken, while there is
                // still a month left to do something about it.
                row.projectedOvershoot?.let { overshoot ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.TrendingUp,
                            contentDescription = null,
                            tint = Warning,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "К концу месяца ≈ ${formatMoneyWhole(row.forecast!!.projected)}, " +
                                "это на ${formatMoneyWhole(overshoot)} больше лимита",
                            style = MaterialTheme.typography.bodySmall,
                            color = Warning
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    editingRow?.let { row ->
        var limitText by remember(row) {
            mutableStateOf(row.limit?.let { formatEditableLimit(it) } ?: "")
        }
        val limitValue = limitText.replace(',', '.').replace(" ", "").toDoubleOrNull()

        AlertDialog(
            onDismissRequest = { editingRow = null },
            title = { Text("Лимит: ${row.category.label}") },
            text = {
                Column {
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it },
                        label = { Text("Лимит в месяц, ₸") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = limitText.isNotEmpty() && limitValue == null
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Пустое поле — убрать лимит.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setLimit(row.category, limitValue)
                        editingRow = null
                    },
                    enabled = limitText.isEmpty() || limitValue != null
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { editingRow = null }) { Text("Отмена") }
            }
        )
    }
}

private fun formatEditableLimit(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/**
 * Everything the month adds up to, above the per-category list: what is spent,
 * how much of the limits that eats, and how many categories are still running
 * without one.
 */
@Composable
private fun BudgetsSummary(rows: List<BudgetRow>) {
    val withLimit = rows.filter { it.limit != null }
    val spent = rows.sumOf { it.spentThisMonth }
    val limitTotal = withLimit.sumOf { it.limit ?: 0.0 }
    val over = withLimit.count { it.isOver }

    AppCard(
        modifier = Modifier.padding(top = 12.dp),
        accent = if (over > 0) Spend else Amber
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ПОТРАЧЕНО В ЭТОМ МЕСЯЦЕ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatMoneyWhole(spent),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (withLimit.isEmpty()) {
                        "Лимитов пока нет — нажми категорию, чтобы задать"
                    } else {
                        "Под лимитом ${withLimit.size} из ${rows.size} категорий" +
                            if (over > 0) " · превышено: $over" else ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over > 0) Spend else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (limitTotal > 0) {
                val used = (withLimit.sumOf { it.spentThisMonth } / limitTotal).toFloat()
                RingGauge(
                    progress = used,
                    modifier = Modifier.size(76.dp),
                    diameter = 76.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    progressColor = if (used >= 1f) Spend else Amber
                ) {
                    Text(
                        text = "${(used * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
