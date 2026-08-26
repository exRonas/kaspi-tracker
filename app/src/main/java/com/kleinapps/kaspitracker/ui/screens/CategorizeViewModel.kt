package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Feeds the "needs review" queue: transactions the auto-classifier couldn't
 * place — unrecognized merchants, and every outgoing transfer (those pay for
 * anything, so only the user knows what they were for).
 */
class CategorizeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TransactionRepository.get(app)

    val queue: StateFlow<List<Transaction>> = repo.observeUncategorized()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Assigns [category] and removes the row from the queue. The choice is also
     * remembered for this merchant, so the same shop is categorized on its own
     * next time and any other queued rows for it are cleared at once.
     */
    fun assign(tx: Transaction, category: Category) {
        viewModelScope.launch { repo.assignCategory(tx, category) }
    }
}
