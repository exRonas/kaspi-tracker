package com.kleinapps.kaspitracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TxType
import com.kleinapps.kaspitracker.util.AmountExpression

/**
 * Bottom sheet for editing any transaction: amount, merchant, type, category,
 * account. Saving returns the updated copy via [onSave].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionSheet(
    tx: Transaction,
    onSave: (Transaction) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(tx.amount?.let { formatEditable(it) } ?: "") }
    var merchant by remember { mutableStateOf(tx.merchant ?: "") }
    var account by remember { mutableStateOf(tx.account ?: "") }
    var type by remember { mutableStateOf(tx.type) }
    var category by remember { mutableStateOf(tx.category) }
    var shared by remember { mutableStateOf(tx.shared) }
    var excluded by remember { mutableStateOf(tx.excludedFromStats) }

    // Editing an existing amount is where "/2" and "-800" earn their keep: the
    // stored amount is the base they continue from.
    val amountValue = AmountExpression.evaluate(amountText, base = tx.amount)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Редактировать операцию",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            AmountField(
                value = amountText,
                onValueChange = { amountText = it },
                computed = amountValue,
                label = "Сумма, ${tx.currency}",
                base = tx.amount,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Мерчант / кому") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = account,
                onValueChange = { account = it },
                label = { Text("Счёт (например, Kaspi Gold)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "Тип",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TxType.PURCHASE, TxType.TRANSFER_OUT, TxType.TRANSFER_IN).forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t },
                        label = { Text(t.label()) }
                    )
                }
            }

            Text(
                text = "Категория",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            CategoryPickerGrid(
                selected = category,
                onSelect = { category = it }
            )

            SwitchRow(
                title = "Общее",
                subtitle = "Трата на вас двоих — попадёт в «Вместе»",
                checked = shared,
                onCheckedChange = { shared = it }
            )
            SwitchRow(
                title = "Не учитывать в статистике",
                subtitle = "Остаётся в истории, но не входит в суммы",
                checked = excluded,
                onCheckedChange = { excluded = it }
            )

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onSave(
                        tx.copy(
                            amount = amountValue ?: tx.amount,
                            merchant = merchant.takeIf { it.isNotBlank() },
                            account = account.takeIf { it.isNotBlank() },
                            type = type,
                            category = category ?: Category.OTHER,
                            shared = shared,
                            excludedFromStats = excluded
                        )
                    )
                },
                enabled = amountText.isEmpty() || amountValue != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить")
            }
        }
    }
}

private fun formatEditable(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** A labelled switch whose whole row is the touch target. */
@Composable
fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            // The theme's default "off" track is too faint to read as a control.
            colors = SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
