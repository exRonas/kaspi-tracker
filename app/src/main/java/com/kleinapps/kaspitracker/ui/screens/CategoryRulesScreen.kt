package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Rule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.CategoryRule
import com.kleinapps.kaspitracker.data.TransactionRepository
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.CategoryChip
import com.kleinapps.kaspitracker.ui.components.CategoryPickerGrid
import com.kleinapps.kaspitracker.ui.components.EmptyState
import com.kleinapps.kaspitracker.ui.components.RowDivider
import com.kleinapps.kaspitracker.ui.theme.Kaspi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryRulesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TransactionRepository.get(app)

    val rules: StateFlow<List<CategoryRule>> = repo.observeCategoryRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun change(rule: CategoryRule, category: Category) {
        viewModelScope.launch { repo.updateRule(rule, category) }
    }

    fun forget(rule: CategoryRule) {
        viewModelScope.launch { repo.forgetRule(rule.merchantKey) }
    }
}

/**
 * The merchants the app has learned, and what it learned about them. Everything
 * here was taught by the user answering the review queue (or editing an
 * operation) — this screen is where a wrong answer gets corrected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRulesScreen(viewModel: CategoryRulesViewModel = viewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryRule?>(null) }

    if (rules.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Outlined.Rule,
                title = "Правил пока нет",
                subtitle = "Как только выберешь категорию для операции, магазин запомнится и появится здесь"
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                text = "${rules.size} ${shopsWord(rules.size)}. Нажми, чтобы сменить категорию.",
                style = Kaspi.type.bodyS,
                color = Kaspi.colors.ink2,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
        }
        item {
            AppCard(contentPadding = 0.dp) {
                rules.forEachIndexed { index, rule ->
                    if (index > 0) RowDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = rule }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = rule.merchantKey.replaceFirstChar { it.uppercase() },
                            style = Kaspi.type.bodyL,
                            color = Kaspi.colors.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Kaspi.colors.ink3,
                            modifier = Modifier.padding(horizontal = 8.dp).size(16.dp)
                        )
                        CategoryChip(rule.category, modifier = Modifier.widthIn(max = 160.dp))
                    }
                }
            }
        }
    }

    editing?.let { rule ->
        ModalBottomSheet(onDismissRequest = { editing = null }, containerColor = Kaspi.colors.surf) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = rule.merchantKey.replaceFirstChar { it.uppercase() },
                    style = Kaspi.type.titleL,
                    color = Kaspi.colors.ink
                )
                Text(
                    text = "Новая категория применится и к неразобранным операциям этого магазина.",
                    style = Kaspi.type.bodyS,
                    color = Kaspi.colors.ink2
                )
                CategoryPickerGrid(
                    selected = rule.category,
                    onSelect = {
                        viewModel.change(rule, it)
                        editing = null
                    }
                )
                TextButton(onClick = {
                    viewModel.forget(rule)
                    editing = null
                }) {
                    Text("Забыть правило", color = Kaspi.colors.neg, style = Kaspi.type.bodyL)
                }
            }
        }
    }
}

/** "1 запомненный магазин", "3 запомненных магазина", "23 запомненных магазина". */
private fun shopsWord(count: Int): String {
    val lastTwo = count % 100
    val last = count % 10
    return when {
        lastTwo in 11..14 -> "запомненных магазинов"
        last == 1 -> "запомненный магазин"
        last in 2..4 -> "запомненных магазина"
        else -> "запомненных магазинов"
    }
}
