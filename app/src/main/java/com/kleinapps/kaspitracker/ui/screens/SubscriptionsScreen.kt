package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.Subscription
import com.kleinapps.kaspitracker.data.TransactionRepository
import com.kleinapps.kaspitracker.ui.components.CategoryAvatar
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.EmptyState
import com.kleinapps.kaspitracker.ui.theme.Cyan
import com.kleinapps.kaspitracker.util.formatDayHeader
import com.kleinapps.kaspitracker.util.formatMoney
import com.kleinapps.kaspitracker.util.formatMoneyWhole
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SubscriptionsViewModel(app: Application) : AndroidViewModel(app) {

    val subscriptions: StateFlow<List<Subscription>> =
        TransactionRepository.get(app).observeSubscriptions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Regular payments pulled out of the history: what repeats, how much it costs
 * per month and when the next charge is due.
 */
@Composable
fun SubscriptionsScreen(viewModel: SubscriptionsViewModel = viewModel()) {
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()

    if (subscriptions.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Filled.Autorenew,
                title = "Подписок не видно",
                subtitle = "Они появятся, когда одна и та же трата повторится через " +
                    "неделю, месяц или год.",
                tint = Cyan
            )
        }
        return
    }

    val confirmed = subscriptions.filter { it.confirmed }
    val monthlyTotal = confirmed.sumOf { it.monthlyCost }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            AppCard(modifier = Modifier.padding(top = 12.dp), accent = Cyan) {
                Text(
                    text = "ПОДПИСКИ СЪЕДАЮТ В МЕСЯЦ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatMoneyWhole(monthlyTotal),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${confirmed.size} подтверждённых, " +
                        "${subscriptions.size - confirmed.size} под вопросом",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(subscriptions, key = { it.merchantKey }) { subscription ->
            SubscriptionCard(subscription)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SubscriptionCard(subscription: Subscription) {
    AppCard(contentPadding = 14.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CategoryAvatar(subscription.category ?: Category.OTHER, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = subscription.merchant,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = buildString {
                        append(subscription.cadence?.label ?: "похоже на подписку")
                        append(" · ")
                        append("платежей: ${subscription.payments}")
                        val next = subscription.nextExpected
                        if (next != null) {
                            append(" · след. ${formatDayHeader(next)}")
                        } else {
                            append(" · последний ${formatDayHeader(subscription.lastPaid)}")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatMoney(subscription.amount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
