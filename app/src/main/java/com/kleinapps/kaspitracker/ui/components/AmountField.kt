package com.kleinapps.kaspitracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kleinapps.kaspitracker.util.AmountExpression
import com.kleinapps.kaspitracker.util.formatMoney

/**
 * Money input that doubles as a calculator cell: `3600/2` splits a shared bill,
 * and `-800` / `/2` continue from [base] — the amount already on the record.
 * The computed value is shown live underneath so the arithmetic is never a
 * surprise after saving.
 */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    computed: Double?,
    modifier: Modifier = Modifier,
    label: String = "Сумма, ₸",
    base: Double? = null
) {
    val isFormula = AmountExpression.isFormula(value)

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            // Not KeyboardType.Decimal: the formula needs / * ( ) too, and the
            // text keyboard is the only one that offers them everywhere.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
            isError = value.isNotEmpty() && computed == null,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = when {
                value.isEmpty() ->
                    "Можно считать прямо здесь: 3600/2, -800, (500+1200)/2"
                computed == null && base == null && value.firstOrNull() in listOf('/', '*', '+') ->
                    "Так можно править уже сохранённую сумму — здесь нужно полное выражение"
                computed == null -> "Не могу посчитать — проверь выражение"
                isFormula -> "= ${formatMoney(computed)}"
                else -> formatMoney(computed)
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (computed == null && value.isNotEmpty()) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }
}
