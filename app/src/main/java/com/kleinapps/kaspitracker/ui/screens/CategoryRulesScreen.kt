package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.CategoryRule
import com.kleinapps.kaspitracker.data.TransactionRepository
import com.kleinapps.kaspitracker.ui.components.CategoryAvatar
import com.kleinapps.kaspitracker.ui.components.AppCard
import com.kleinapps.kaspitracker.ui.components.EmptyState
import com.kleinapps.kaspitracker.ui.components.CategoryPickerGrid
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
                icon = Icons.Filled.Rule,
                title = "Правил пока нет",
                subtitle = "Как только выберешь категорию для операции, магазин " +
                    "запомнится и появится здесь."
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "${rules.size} запомненных магазинов. Нажми, чтобы сменить категорию.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        items(rules, key = { it.merchantKey }) { rule ->
            AppCard(onClick = { editing = rule }, contentPadding = 12.dp) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CategoryAvatar(rule.category, size = 38.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rule.merchantKey,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = rule.category.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.forget(rule) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Забыть правило",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    editing?.let { rule ->
        ModalBottomSheet(onDismissRequest = { editing = null }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = rule.merchantKey,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Новая категория применится и к неразобранным операциям этого магазина.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CategoryPickerGrid(
                    selected = rule.category,
                    onSelect = {
                        viewModel.change(rule, it)
                        editing = null
                    },
                    categories = Category.entries
                )
            }
        }
    }
}
