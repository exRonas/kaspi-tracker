package com.kleinapps.kaspitracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.TxType
import com.kleinapps.kaspitracker.ui.components.AmountField
import com.kleinapps.kaspitracker.ui.components.CategoryPickerGrid
import com.kleinapps.kaspitracker.ui.components.label
import com.kleinapps.kaspitracker.util.AmountExpression
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onSaved: () -> Unit,
    viewModel: AddTransactionViewModel = viewModel()
) {
    var amountText by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxType.PURCHASE) }
    var category by remember { mutableStateOf<Category?>(null) }
    var timestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    // The field is an expression, not just a number: "3600/2" splits a shared
    // bill in place. Grouping spaces and both decimal separators are handled
    // inside the evaluator.
    val amountValue = AmountExpression.evaluate(amountText)
    val canSave = amountValue != null && amountValue > 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Without this the category grid and the save button below it are
            // unreachable on a short screen.
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Тип операции",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
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

        AmountField(
            value = amountText,
            onValueChange = { amountText = it },
            computed = amountValue,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text("Кому / у кого (необязательно)") },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = "Категория (необязательно)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Если не выбрать — подберётся по названию, а незнакомые попадут в «Разобрать».",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CategoryPickerGrid(selected = category, onSelect = { picked ->
            category = if (category == picked) null else picked
        })

        Text(
            text = "Дата и время",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showDatePicker = true }) {
                Text(SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(timestamp))
            }
            OutlinedButton(onClick = { showTimePicker = true }) {
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(timestamp))
            }
        }

        Button(
            onClick = {
                viewModel.save(
                    amount = amountValue!!,
                    type = type,
                    merchant = merchant,
                    category = category,
                    timestamp = timestamp,
                    onDone = onSaved
                )
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Сохранить")
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = timestamp)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { pickedDate ->
                        timestamp = combineDateKeepTime(pickedDate, timestamp)
                    }
                    showDatePicker = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val state = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    timestamp = combineTimeKeepDate(state.hour, state.minute, timestamp)
                    showTimePicker = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Отмена") }
            },
            text = { TimePicker(state = state) }
        )
    }
}

private fun combineDateKeepTime(newDateMillis: Long, oldMillis: Long): Long {
    // DatePicker reports selectedDateMillis as UTC midnight — read it back in
    // UTC too, otherwise local timezones ahead of UTC see the previous day.
    val datePart = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = newDateMillis }
    val timePart = Calendar.getInstance().apply { timeInMillis = oldMillis }
    return Calendar.getInstance().apply {
        set(
            datePart.get(Calendar.YEAR),
            datePart.get(Calendar.MONTH),
            datePart.get(Calendar.DAY_OF_MONTH),
            timePart.get(Calendar.HOUR_OF_DAY),
            timePart.get(Calendar.MINUTE),
            0
        )
    }.timeInMillis
}

private fun combineTimeKeepDate(hour: Int, minute: Int, oldMillis: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = oldMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
    }.timeInMillis
}
