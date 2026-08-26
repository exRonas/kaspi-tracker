package com.kleinapps.kaspitracker.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TransactionRepository
import com.kleinapps.kaspitracker.data.TxSource
import com.kleinapps.kaspitracker.data.TxType
import kotlinx.coroutines.launch

class AddTransactionViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = TransactionRepository.get(app)

    fun save(
        amount: Double,
        type: TxType,
        merchant: String?,
        category: Category?,
        timestamp: Long,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val cleanMerchant = merchant?.takeIf { it.isNotBlank() }
            repo.insert(
                Transaction(
                    timestamp = timestamp,
                    title = null,
                    rawText = "Добавлено вручную",
                    amount = amount,
                    type = type,
                    merchant = cleanMerchant,
                    source = TxSource.MANUAL,
                    // Null lets the classifier (and learned rules) decide.
                    category = category
                )
            )
            // Picking a category by hand teaches it for this merchant, exactly
            // like answering in the review queue does.
            if (category != null) repo.learn(cleanMerchant, category)
            onDone()
        }
    }
}
