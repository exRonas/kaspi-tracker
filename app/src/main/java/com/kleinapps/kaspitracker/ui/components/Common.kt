package com.kleinapps.kaspitracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TxType
import com.kleinapps.kaspitracker.ui.theme.Income
import com.kleinapps.kaspitracker.ui.theme.Spend
import com.kleinapps.kaspitracker.util.formatMoney
import com.kleinapps.kaspitracker.util.formatTime

fun TxType.label(): String = when (this) {
    TxType.PURCHASE -> "Покупка"
    TxType.TRANSFER_OUT -> "Перевод"
    TxType.TRANSFER_IN -> "Пополнение"
    TxType.UNKNOWN -> "Не распознано"
}

@Composable
fun TxType.accent(): Color = when (this) {
    TxType.TRANSFER_IN -> Income
    TxType.PURCHASE, TxType.TRANSFER_OUT -> Spend
    TxType.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** One-line operation row with a category icon badge. Used by Home and History. */
@Composable
fun TransactionRow(
    tx: Transaction,
    modifier: Modifier = Modifier
) {
    val category = tx.category ?: Category.OTHER
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CategoryAvatar(category)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.merchant ?: tx.type.label(),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = buildString {
                    append(category.label)
                    append(" · ")
                    append(formatTime(tx.timestamp))
                    tx.account?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        val prefix = when (tx.type) {
            TxType.TRANSFER_IN -> "+"
            else -> if (tx.amount != null) "−" else ""
        }
        Text(
            text = if (tx.amount == null) "—" else prefix + formatMoney(tx.amount, tx.currency),
            style = MaterialTheme.typography.titleMedium,
            color = tx.type.accent(),
            fontWeight = FontWeight.SemiBold
        )
    }
}
