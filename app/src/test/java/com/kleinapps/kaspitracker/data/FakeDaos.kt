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

    override suspend fun getByIds(ids: List<Long>): List<Transaction> =
        rows.value.filter { it.id in ids }

    override suspend fun markSynced(uuid: String, hash: Int) {
        rows.value = rows.value.map { if (it.uuid == uuid) it.copy(syncedHash = hash) else it }
    }

    override suspend fun clearSyncState() {
        rows.value = rows.value.map { it.copy(syncedHash = null) }
    }

    override suspend fun getAllOnce(): List<Transaction> = rows.value.sortedBy { it.timestamp }

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

    override suspend fun getNotificationTimestamps(): List<Long> =
        rows.value.filter { it.source == TxSource.NOTIFICATION }
            .sortedByDescending { it.timestamp }
            .take(200)
            .map { it.timestamp }

    override suspend fun getRecentWithAccount(): List<Transaction> =
        rows.value.filter { it.amount != null && it.account != null }
            .sortedByDescending { it.timestamp }
            .take(400)

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
            list.filter {
                it.timestamp in from..to && it.type.isSpending && !it.excludedFromStats
            }.sumOf { it.amount ?: 0.0 }
        }

    override fun observeBetween(from: Long, to: Long): Flow<List<Transaction>> =
        rows.map { list ->
            list.filter { it.amount != null && it.timestamp in from..to }.sortedBy { it.timestamp }
        }

    override fun observeIncomeBetween(from: Long, to: Long): Flow<Double> =
        rows.map { list ->
            list.filter {
                it.timestamp in from..to && it.type == TxType.TRANSFER_IN && !it.excludedFromStats
            }.sumOf { it.amount ?: 0.0 }
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

class FakeSharedRuleDao : SharedRuleDao {

    val rules = MutableStateFlow<List<SharedRule>>(emptyList())

    override suspend fun upsert(rule: SharedRule) {
        rules.value = rules.value.filterNot { it.merchantKey == rule.merchantKey } + rule
    }

    override suspend fun getAll(): List<SharedRule> = rules.value
}

class FakePendingDeleteDao : PendingDeleteDao {

    val rows = MutableStateFlow<List<PendingDelete>>(emptyList())

    override suspend fun insert(row: PendingDelete) {
        if (rows.value.none { it.uuid == row.uuid }) rows.value = rows.value + row
    }

    override suspend fun getAll(): List<PendingDelete> = rows.value

    override suspend fun deleteByUuids(uuids: List<String>) {
        rows.value = rows.value.filterNot { it.uuid in uuids }
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}

class FakePartnerTransactionDao : PartnerTransactionDao {

    val rows = MutableStateFlow<List<PartnerTransaction>>(emptyList())

    override suspend fun upsertAll(rows: List<PartnerTransaction>) {
        val incoming = rows.associateBy { it.uuid }
        this.rows.value = this.rows.value.filterNot { it.uuid in incoming } + rows
    }

    override suspend fun deleteByUuids(uuids: List<String>) {
        rows.value = rows.value.filterNot { it.uuid in uuids }
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }

    override fun observeAll(): Flow<List<PartnerTransaction>> =
        rows.map { list -> list.filter { it.amount != null }.sortedByDescending { it.timestamp } }
}

/** Repository over fresh in-memory DAOs; pass the ones a test wants to inspect. */
fun fakeRepository(
    dao: FakeTransactionDao = FakeTransactionDao(),
    ruleDao: FakeCategoryRuleDao = FakeCategoryRuleDao(),
    sharedRuleDao: FakeSharedRuleDao = FakeSharedRuleDao(),
    pendingDeleteDao: FakePendingDeleteDao = FakePendingDeleteDao(),
    partnerDao: FakePartnerTransactionDao = FakePartnerTransactionDao(),
    sharedHintsEnabled: () -> Boolean = { true }
) = TransactionRepository(dao, ruleDao, sharedRuleDao, pendingDeleteDao, partnerDao, sharedHintsEnabled)
