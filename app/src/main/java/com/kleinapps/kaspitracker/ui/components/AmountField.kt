package com.kleinapps.kaspitracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.util.AmountExpression
import com.kleinapps.kaspitracker.util.formatMoney

/**
 * Amount entry that is also a calculator: "3600/2", "-800", "(500+1200)/2".
 * The computed value is shown live underneath so the arithmetic is never a
 * surprise after saving. With a [base] (editing a saved row), a leading
 * operator continues from it: "/2" halves the stored amount.
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
    val broken = value.isNotEmpty() && computed == null

    Column(modifier = modifier) {
        FieldLabel(label)
        KaspiTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "0",
            textStyle = Kaspi.type.headline,
            // Not KeyboardType.Decimal: the formula needs / * ( ) too, and the
            // text keyboard is the only one that offers them everywhere.
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            isError = broken
        )
        Text(
            text = when {
                value.isEmpty() -> "Можно считать прямо здесь: 3600/2, (500+1200)/2"
                computed == null && base == null && value.firstOrNull() in listOf('/', '*', '+') ->
                    "Так можно править уже сохранённую сумму — здесь нужно полное выражение"
                computed == null -> "Не могу посчитать — проверь выражение"
                isFormula -> "= ${formatMoney(computed)}"
                base != null -> "= ${formatMoney(computed)} · введи /2, чтобы поделить"
                else -> formatMoney(computed)
            },
            style = Kaspi.type.label,
            color = when {
                broken -> Kaspi.colors.neg
                isFormula || base != null -> Kaspi.colors.accent
                else -> Kaspi.colors.ink2
            },
            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
        )
    }
}
