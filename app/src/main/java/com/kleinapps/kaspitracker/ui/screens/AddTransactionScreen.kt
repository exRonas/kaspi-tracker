package com.kleinapps.kaspitracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.TxType
import com.kleinapps.kaspitracker.ui.components.AmountField
import com.kleinapps.kaspitracker.ui.components.CategoryPickerGrid
import com.kleinapps.kaspitracker.ui.components.EditableTypes
import com.kleinapps.kaspitracker.ui.components.FieldLabel
import com.kleinapps.kaspitracker.ui.components.KaspiTextField
import com.kleinapps.kaspitracker.ui.components.PrimaryButton
import com.kleinapps.kaspitracker.ui.components.SegmentedChoice
import com.kleinapps.kaspitracker.ui.components.SharedChoice
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.ui.theme.Radius
import com.kleinapps.kaspitracker.util.AmountExpression
import com.kleinapps.kaspitracker.util.formatDayHeader
import com.kleinapps.kaspitracker.util.formatTime
import java.util.Calendar
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
    var shared by remember { mutableStateOf(false) }
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AmountField(
            value = amountText,
            onValueChange = { amountText = it },
            computed = amountValue
        )
        Column {
            FieldLabel("Тип операции")
            SegmentedChoice(options = EditableTypes, selected = type, onSelect = { type = it })
        }
        Column {
            FieldLabel("Чья трата")
            SharedChoice(shared = shared, onChange = { shared = it })
        }
        Column {
            FieldLabel("Кому / у кого (необязательно)")
            KaspiTextField(
                value = merchant,
                onValueChange = { merchant = it },
                placeholder = "Например, рынок «Зелёный базар»"
            )
        }
        Column {
            FieldLabel("Дата и время")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.field))
                    .background(Kaspi.colors.bg)
                    .border(1.dp, Kaspi.colors.line, RoundedCornerShape(Radius.field))
                    .clickable { showDatePicker = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Event, contentDescription = null, tint = Kaspi.colors.ink2)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${formatDayHeader(timestamp)}, ${formatTime(timestamp)}",
                    style = Kaspi.type.bodyL,
                    color = Kaspi.colors.ink,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "время",
                    style = Kaspi.type.label,
                    color = Kaspi.colors.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.bar))
                        .clickable { showTimePicker = true }
                        .padding(4.dp)
                )
            }
        }
        Column {
            FieldLabel("Категория (необязательно)")
            CategoryPickerGrid(selected = category, onSelect = { picked ->
                category = if (category == picked) null else picked
            })
            Text(
                text = "Если не выбрать — подберётся по названию, а незнакомые попадут в «Разобрать»",
                style = Kaspi.type.label,
                color = Kaspi.colors.ink2,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        PrimaryButton(
            text = "Сохранить",
            onClick = {
                viewModel.save(
                    amount = amountValue!!,
                    type = type,
                    merchant = merchant,
                    category = category,
                    shared = shared,
                    timestamp = timestamp,
                    onDone = onSaved
                )
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth()
        )
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
                    showTimePicker = true
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
