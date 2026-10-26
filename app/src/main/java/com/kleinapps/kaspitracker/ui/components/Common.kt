package com.kleinapps.kaspitracker.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TxSource
import com.kleinapps.kaspitracker.data.TxType
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import com.kleinapps.kaspitracker.util.formatMoney
import com.kleinapps.kaspitracker.util.formatTime

fun TxType.label(): String = when (this) {
    TxType.PURCHASE -> "Покупка"
    TxType.TRANSFER_OUT -> "Перевод"
    TxType.TRANSFER_IN -> "Пополнение"
    TxType.UNKNOWN -> "Не распознано"
}

/** Where a row came from, as a small glyph before its subtitle. */
val TxSource.icon: ImageVector
    get() = when (this) {
        TxSource.NOTIFICATION -> Icons.Outlined.Notifications
        TxSource.RECEIPT -> Icons.Outlined.PictureAsPdf
        TxSource.MANUAL -> Icons.Outlined.EditNote
    }

/** "+15 000 ₸" / "−6 840 ₸". */
fun Transaction.signedAmount(): String = when {
    amount == null -> "—"
    type == TxType.TRANSFER_IN -> "+" + formatMoney(amount, currency)
    else -> "−" + formatMoney(amount, currency)
}

/** Whose row it is, where both people's rows are mixed: initial on their colour. */
data class OwnerMark(val initial: String, val color: Color)

/**
 * One operation: category tile, merchant, "source · category · card", and on
 * the right the amount over the badges and time.
 *
 * [selected] non-null puts the row in pick mode with a checkbox; [owner] marks
 * whose operation it is in the "together" view.
 */
@Composable
fun TransactionRow(
    tx: Transaction,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    owner: OwnerMark? = null
) {
    val category = tx.category ?: Category.OTHER
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected != null) {
            // Not clickable on its own: the whole row toggles.
            Checkbox(
                checked = selected,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = Kaspi.colors.accent,
                    uncheckedColor = Kaspi.colors.ink3,
                    checkmarkColor = Kaspi.colors.onAcc
                )
            )
            Spacer(Modifier.width(12.dp))
        }
        Box {
            CategoryAvatar(category)
            if (owner != null) {
                PersonAvatar(
                    initial = owner.initial,
                    color = owner.color,
                    size = 18.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 5.dp, y = 5.dp)
                        .border(2.dp, Kaspi.colors.surf, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tx.merchant ?: tx.type.label(),
                style = Kaspi.type.bodyL,
                color = Kaspi.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(tx.source.icon, contentDescription = null, tint = Kaspi.colors.ink3, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text = listOfNotNull(category.label, tx.account).joinToString(" · "),
                    style = Kaspi.type.bodyS,
                    color = Kaspi.colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = tx.signedAmount(),
                style = Kaspi.type.money,
                color = when {
                    tx.excludedFromStats -> Kaspi.colors.ink3
                    tx.type == TxType.TRANSFER_IN -> Kaspi.colors.pos
                    else -> Kaspi.colors.ink
                },
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (tx.excludedFromStats) ExcludedBadge() else if (tx.shared) SharedBadge()
                Text(text = formatTime(tx.timestamp), style = Kaspi.type.label, color = Kaspi.colors.ink2)
            }
        }
    }
}
