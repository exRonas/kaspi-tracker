package com.kleinapps.kaspitracker.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory stand-ins for the Room DAOs, so the repository runs on plain JVM. */
class FakeTransactionDao : TransactionDao {

    val rows = MutableStateFlow<List<Transaction>>(emptyList())
    private var nextId = 1L

    override suspend fun insert(transaction: Transaction): Long {
        val id = nextId++
        rows.value = rows.value + transaction.copy(id = id)
        return id
    }

    override suspend fun update(transaction: Transaction) {
        rows.value = rows.value.map { if (it.id == transaction.id) transaction else it }
    }

    override suspend fun delete(transaction: Transaction) {
        rows.value = rows.value.filterNot { it.id == transaction.id }
    }

    override suspend fun getById(id: Long): Transaction? = rows.value.firstOrNull { it.id == id }

    override suspend fun getAllOnce(): List<Transaction> = rows.value.sortedBy { it.timestamp }

    override fun observeBalances(): Flow<List<AccountBalance>> =
        rows.map { list ->
            list.filter { it.balance != null && it.account != null }
                .groupBy { it.account!! }
                .map { (account, txs) ->
                    val newest = txs.maxBy { it.timestamp }
                    AccountBalance(account, newest.balance!!, newest.timestamp)
                }
        }

    override fun observeBalancePoints(): Flow<List<AccountBalance>> =
        rows.map { list ->
            list.filter { it.balance != null && it.account != null }
                .sortedByDescending { it.timestamp }
                .take(60)
                .map { AccountBalance(it.account!!, it.balance!!, it.timestamp) }
        }

    override suspend fun findNotificationForReceipt(
        amount: Double,
        timestamp: Long,
        from: Long,
        to: Long
    ): Transaction? =
        rows.value
            .filter {
                it.source == TxSource.NOTIFICATION && it.amount == amount && it.timestamp in from..to
            }
            .minByOrNull { kotlin.math.abs(it.timestamp - timestamp) }

    override fun observeSpendings(): Flow<List<Transaction>> =
        rows.map { list ->
            list.filter { it.amount != null && it.type.isSpending }.sortedBy { it.timestamp }
        }

    override suspend fun sumSpentInCategory(category: Category, from: Long, to: Long): Double =
        rows.value
            .filter {
                it.amount != null && it.type.isSpending &&
                    it.category == category && it.timestamp in from..to
            }
            .sumOf { it.amount ?: 0.0 }

    override suspend fun findByDedupKey(dedupKey: String, from: Long, to: Long): Transaction? =
        rows.value.filter { it.dedupKey == dedupKey && it.timestamp in from..to }
            .minByOrNull { it.timestamp }

    override suspend fun getCategorized(): List<Transaction> =
        rows.value.filter { it.category != null && it.merchant != null }.sortedBy { it.timestamp }

    override suspend fun getNotificationsWithoutMerchant(): List<Transaction> =
        rows.value.filter {
            it.source == TxSource.NOTIFICATION && (it.merchant == null || it.balance == null)
        }

    override suspend fun getUncategorizedWithMerchant(): List<Transaction> =
        rows.value.filter { it.category == null && it.merchant != null }

    override suspend fun getUncategorized(): List<Transaction> =
        rows.value.filter { it.category == null }

    override fun observeUncategorized(): Flow<List<Transaction>> =
        rows.map { list ->
            list.filter { it.category == null && it.amount != null }.sortedByDescending { it.timestamp }
        }

    override fun observeAccounts(): Flow<List<String>> =
        rows.map { list -> list.mapNotNull { it.account }.distinct().sorted() }

    override suspend fun countReceiptDuplicates(timestamp: Long, amount: Double?): Int =
        rows.value.count {
            it.source == TxSource.RECEIPT && it.timestamp == timestamp && it.amount == amount
        }

    override suspend fun clearAll() {
        rows.value = emptyList()
    }

    override fun observeAll(): Flow<List<Transaction>> =
        rows.map { list -> list.sortedByDescending { it.timestamp } }

    override fun observeAllWithAmount(): Flow<List<Transaction>> =
        rows.map { list -> list.filter { it.amount != null }.sortedByDescending { it.timestamp } }

    override fun observeRecent(limit: Int): Flow<List<Transaction>> =
        observeAllWithAmount().map { it.take(limit) }

    override fun observeSpentBetween(from: Long, to: Long): Flow<Double> =
        rows.map { list ->
            list.filter { it.timestamp in from..to && it.type.isSpending }
                .sumOf { it.amount ?: 0.0 }
        }

    override fun observeBetween(from: Long, to: Long): Flow<List<Transaction>> =
        rows.map { list ->
            list.filter { it.amount != null && it.timestamp in from..to }.sortedBy { it.timestamp }
        }

    override fun observeIncomeBetween(from: Long, to: Long): Flow<Double> =
        rows.map { list ->
            list.filter { it.timestamp in from..to && it.type == TxType.TRANSFER_IN }
                .sumOf { it.amount ?: 0.0 }
        }
}

class FakeCategoryRuleDao : CategoryRuleDao {

    val rules = MutableStateFlow<List<CategoryRule>>(emptyList())

    override suspend fun upsert(rule: CategoryRule) {
        rules.value = rules.value.filterNot { it.merchantKey == rule.merchantKey } + rule
    }

    override suspend fun getAll(): List<CategoryRule> = rules.value

    override fun observeAll(): Flow<List<CategoryRule>> = rules

    override suspend fun delete(merchantKey: String) {
        rules.value = rules.value.filterNot { it.merchantKey == merchantKey }
    }
}

class FakeBudgetDao : BudgetDao {

    val budgets = MutableStateFlow<List<Budget>>(emptyList())

    override suspend fun getAllOnce(): List<Budget> = budgets.value

    override suspend fun upsert(budget: Budget) {
        budgets.value = budgets.value.filterNot { it.category == budget.category } + budget
    }

    override suspend fun delete(budget: Budget) {
        budgets.value = budgets.value.filterNot { it.category == budget.category }
    }

    override fun observeAll(): Flow<List<Budget>> = budgets
}
