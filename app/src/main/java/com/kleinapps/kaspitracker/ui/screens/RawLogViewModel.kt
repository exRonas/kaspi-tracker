package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RawLogViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TransactionRepository.get(app)

    val log: StateFlow<List<Transaction>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearAll() {
        viewModelScope.launch { repo.clearAll() }
    }
}
