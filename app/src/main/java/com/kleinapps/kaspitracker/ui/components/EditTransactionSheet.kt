package com.kleinapps.kaspitracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.util.AmountExpression
import com.kleinapps.kaspitracker.util.formatFullDate
import com.kleinapps.kaspitracker.util.formatMoney
import com.kleinapps.kaspitracker.util.formatTime

/** Operation types offered in forms, in display order. */
val EditableTypes = listOf(TxType.PURCHASE to "Покупка", TxType.TRANSFER_OUT to "Перевод", TxType.TRANSFER_IN to "Пополнение")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionSheet(
    tx: Transaction,
    onSave: (Transaction) -> Unit,
    onDelete: (Transaction) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(tx.amount?.let { formatEditable(it) } ?: "") }
    var merchant by remember { mutableStateOf(tx.merchant ?: "") }
    var account by remember { mutableStateOf(tx.account ?: "") }
    var type by remember { mutableStateOf(if (tx.type == TxType.UNKNOWN) TxType.PURCHASE else tx.type) }
    var category by remember { mutableStateOf(tx.category) }
    var shared by remember { mutableStateOf(tx.shared) }
    var excluded by remember { mutableStateOf(tx.excludedFromStats) }
    var confirmingDelete by remember { mutableStateOf(false) }

    // Editing an existing amount is where "/2" and "-800" earn their keep: the
    // stored amount is the base they continue from.
    val amountValue = AmountExpression.evaluate(amountText, base = tx.amount)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Kaspi.colors.surf) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(text = "Редактировать операцию", style = Kaspi.type.titleL, color = Kaspi.colors.ink)
                Text(
                    text = "${formatFullDate(tx.timestamp)}, ${formatTime(tx.timestamp)}",
                    style = Kaspi.type.bodyS,
                    color = Kaspi.colors.ink2
                )
            }

            AmountField(
                value = amountText,
                onValueChange = { amountText = it },
                computed = amountValue,
                label = "Сумма, ${tx.currency}",
                base = tx.amount
            )
            SegmentedChoice(options = EditableTypes, selected = type, onSelect = { type = it })
            SharedChoice(shared = shared, onChange = { shared = it })

            Column {
                FieldLabel("Мерчант / кому")
                KaspiTextField(value = merchant, onValueChange = { merchant = it })
            }
            Column {
                FieldLabel("Счёт (например, Kaspi Gold)")
                KaspiTextField(value = account, onValueChange = { account = it })
            }
            Column {
                FieldLabel("Категория")
                CategoryPickerGrid(selected = category, onSelect = { category = it })
            }
            SwitchRow(
                title = "Не учитывать в статистике",
                subtitle = "Переводы между своими картами, возвраты",
                checked = excluded,
                onCheckedChange = { excluded = it }
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { confirmingDelete = true }) {
                    Text("Удалить", style = Kaspi.type.bodyL, color = Kaspi.colors.neg)
                }
                Spacer(Modifier.width(8.dp))
                PrimaryButton(
                    text = "Сохранить",
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
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            containerColor = Kaspi.colors.surf,
            title = { Text("Удалить операцию?", style = Kaspi.type.titleL) },
            text = {
                Text(
                    listOfNotNull(tx.merchant, tx.amount?.let { "−" + formatMoney(it, tx.currency) })
                        .joinToString(" · "),
                    style = Kaspi.type.bodyM,
                    color = Kaspi.colors.ink2
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    onDelete(tx)
                }) { Text("Удалить", color = Kaspi.colors.neg) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Отмена", color = Kaspi.colors.ink) }
            }
        )
    }
}

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
            Text(text = title, style = Kaspi.type.bodyL, color = Kaspi.colors.ink)
            subtitle?.let { Text(text = it, style = Kaspi.type.bodyS, color = Kaspi.colors.ink2) }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Kaspi.colors.onAcc,
                checkedTrackColor = Kaspi.colors.accent,
                checkedBorderColor = Kaspi.colors.accent,
                // The default "off" track is too faint to read as a control.
                uncheckedThumbColor = Kaspi.colors.ink2,
                uncheckedTrackColor = Kaspi.colors.surf2,
                uncheckedBorderColor = Kaspi.colors.ink3
            )
        )
    }
}

private fun formatEditable(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
