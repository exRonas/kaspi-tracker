package com.kleinapps.kaspitracker.data

import android.content.Context
import com.kleinapps.kaspitracker.parser.KaspiNotificationParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Thin wrapper over the DAOs. Single source of truth for the whole app. */
class TransactionRepository(
    private val dao: TransactionDao,
    private val ruleDao: CategoryRuleDao,
    private val sharedRuleDao: SharedRuleDao,
    private val pendingDeleteDao: PendingDeleteDao,
    private val partnerDao: PartnerTransactionDao,
    /** Whether new operations inherit the merchant's last shared/own choice. */
    private val sharedHintsEnabled: () -> Boolean = { true }
) {

    /**
     * Serializes notification inserts: Kaspi's duplicate push arrives while the
     * first one is still being written, so without this both would pass the
     * duplicate check.
     */
    private val notificationLock = Mutex()

    /** Every row, including amount-less pushes — Raw Log only, for parser debugging. */
    fun observeAll(): Flow<List<Transaction>> = dao.observeAll()

    /** Real operations only (has a parseable amount) — everything but Raw Log wants this. */
    fun observeAllWithAmount(): Flow<List<Transaction>> = dao.observeAllWithAmount()

    fun observeRecent(limit: Int = 10): Flow<List<Transaction>> = dao.observeRecent(limit)

    /**
     * The operations behind one view (mine, the partner's, or what we share),
     * newest first — what every statistics screen reads.
     */
    fun observeLedger(scope: ViewScope): Flow<List<LedgerEntry>> = when (scope) {
        // Own view needs no partner rows; skip the second query.
        ViewScope.ME -> dao.observeAllWithAmount().map { ledgerFor(scope, it, emptyList()) }
        else -> combine(dao.observeAllWithAmount(), partnerDao.observeAll()) { mine, partner ->
            ledgerFor(scope, mine, partner.map { it.toTransaction() })
        }
    }

    fun observeSpentBetween(from: Long, to: Long): Flow<Double> = dao.observeSpentBetween(from, to)

    fun observeIncomeBetween(from: Long, to: Long): Flow<Double> = dao.observeIncomeBetween(from, to)

    fun observeBetween(from: Long, to: Long): Flow<List<Transaction>> = dao.observeBetween(from, to)

    fun observeAccounts(): Flow<List<String>> = dao.observeAccounts()

    /** Rows still waiting for the user to pick a category — surfaced on Home. */
    fun observeUncategorized(): Flow<List<Transaction>> = dao.observeUncategorized()

    /** Learned merchant -> category rules, for the settings/debug UI. */
    fun observeCategoryRules(): Flow<List<CategoryRule>> = ruleDao.observeAll()

    /** When Kaspi pushes arrived — the watchdog's sample of the normal rhythm. */
    suspend fun notificationTimestamps(): List<Long> = dao.getNotificationTimestamps()

    /** Forgets a learned rule; the merchant goes back to keyword matching. */
    suspend fun forgetRule(merchantKey: String) = ruleDao.delete(merchantKey)

    /** Changes a learned rule and re-applies it to everything still unsorted. */
    suspend fun updateRule(rule: CategoryRule, category: Category) {
        ruleDao.upsert(rule.copy(category = category, updatedAt = System.currentTimeMillis()))
        dao.getUncategorizedWithMerchant()
            .filter { MerchantKey.of(it.merchant) == rule.merchantKey }
            .forEach { dao.update(it.copy(category = category)) }
    }

    suspend fun insert(transaction: Transaction): Long {
        val stored = applyRules(transaction)
        val id = dao.insert(stored)
        markSelfTransfers()
        return id
    }

    /**
     * Insert path for intercepted pushes. Returns the new row id, or null when
     * the same notification was already recorded moments ago — Kaspi posts the
     * group summary and the notification itself as two separate pushes, and
     * Android re-delivers a notification every time it is updated.
     */
    suspend fun insertNotification(transaction: Transaction): Long? = notificationLock.withLock {
        val key = transaction.dedupKey
        val existing = key?.let { findDuplicateNotification(it, transaction.timestamp) }
        if (existing != null) {
            // The two copies of one push don't always carry the same header: one
            // has "Kaspi Gold" as its title, the other has no title at all. Keep
            // whichever fields the stored row is missing.
            val enriched = existing.copy(
                title = existing.title ?: transaction.title,
                account = existing.account ?: transaction.account
            )
            if (enriched != existing) dao.update(enriched)
            return@withLock null
        }
        val stored = applyRules(transaction)
        dao.insert(stored).also { markSelfTransfers() }
    }

    suspend fun findDuplicateNotification(dedupKey: String, timestamp: Long): Transaction? =
        dao.findByDedupKey(
            dedupKey,
            timestamp - NOTIFICATION_DEDUP_WINDOW_MS,
            timestamp + NOTIFICATION_DEDUP_WINDOW_MS
        )

    /** True when an identical receipt (same timestamp + amount) is already stored. */
    suspend fun isReceiptDuplicate(timestamp: Long, amount: Double?): Boolean =
        dao.countReceiptDuplicates(timestamp, amount) > 0

    /**
     * Insert path for an imported receipt PDF.
     *
     * Kaspi pushes a notification the moment a payment goes through and the
     * receipt is shared into the app minutes later — both describe the same
     * money. When a matching push is already stored, the receipt fills in what
     * it knows (real merchant, account, full text) instead of adding a second
     * operation that would double the day's total.
     */
    suspend fun insertReceipt(transaction: Transaction): Long? = notificationLock.withLock {
        val amount = transaction.amount
        val existing = amount?.let {
            dao.findNotificationForReceipt(
                amount = it,
                timestamp = transaction.timestamp,
                from = transaction.timestamp - RECEIPT_MATCH_WINDOW_MS,
                to = transaction.timestamp + RECEIPT_MATCH_WINDOW_MS
            )
        }
        if (existing != null) {
            val merged = existing.copy(
                rawText = transaction.rawText,
                merchant = transaction.merchant ?: existing.merchant,
                account = transaction.account ?: existing.account,
                type = if (existing.type == TxType.UNKNOWN) transaction.type else existing.type,
                source = TxSource.RECEIPT,
                category = existing.category
                    ?: CategoryClassifier.classify(
                        transaction.merchant, transaction.rawText, transaction.type, ruleDao.getAll()
                    )
            )
            dao.update(merged)
            return@withLock null
        }
        val stored = applyRules(transaction)
        dao.insert(stored)
    }

    /**
     * Saves an edited row. When the user changed the category, that choice is
     * remembered for this merchant and applied to everything still waiting in
     * the review queue.
     */
    suspend fun update(transaction: Transaction) {
        val previous = dao.getById(transaction.id)
        dao.update(transaction)
        val category = transaction.category
        if (category != null && previous?.category != category) {
            learn(transaction.merchant, category)
        }
        if (previous != null && previous.shared != transaction.shared) {
            MerchantKey.of(transaction.merchant)?.let { key ->
                sharedRuleDao.upsert(SharedRule(merchantKey = key, shared = transaction.shared))
            }
        }
    }

    /** Category picked from the review queue — same learning as [update]. */
    suspend fun assignCategory(transaction: Transaction, category: Category) {
        dao.update(transaction.copy(category = category))
        learn(transaction.merchant, category)
    }

    suspend fun delete(transaction: Transaction) {
        dao.delete(transaction)
        queueRemoteDelete(transaction)
    }

    /** Deletes the picked rows; the ones the cloud already has are queued for removal there. */
    suspend fun deleteMany(ids: Collection<Long>) {
        dao.getByIds(ids.toList()).forEach { delete(it) }
    }

    /**
     * Marks the picked rows shared or own, and remembers that choice for each of
     * their merchants so the next operation there arrives already marked.
     */
    suspend fun setShared(ids: Collection<Long>, shared: Boolean) {
        val rows = dao.getByIds(ids.toList())
        rows.filter { it.shared != shared }.forEach { dao.update(it.copy(shared = shared)) }
        rows.mapNotNull { MerchantKey.of(it.merchant) }.distinct().forEach { key ->
            sharedRuleDao.upsert(SharedRule(merchantKey = key, shared = shared))
        }
    }

    /** The bulk version of picking a category in the edit sheet — learns the same way. */
    suspend fun setCategory(ids: Collection<Long>, category: Category) {
        val rows = dao.getByIds(ids.toList())
        rows.forEach { dao.update(it.copy(category = category)) }
        rows.map { it.merchant }.distinctBy { MerchantKey.of(it) }.forEach { learn(it, category) }
    }

    /**
     * A row deleted before it ever reached the cloud needs nothing sent; one
     * that did must be removed there too, or the partner keeps seeing it.
     */
    private suspend fun queueRemoteDelete(transaction: Transaction) {
        if (transaction.syncedHash != null) pendingDeleteDao.insert(PendingDelete(transaction.uuid))
    }

    suspend fun clearAll() = dao.clearAll()

    /**
     * Remembers "this merchant means this category" and immediately applies it
     * to every uncategorized row of the same merchant, so answering once clears
     * the whole backlog for that shop instead of one row at a time.
     */
    suspend fun learn(merchant: String?, category: Category) {
        val key = MerchantKey.of(merchant) ?: return
        ruleDao.upsert(CategoryRule(merchantKey = key, category = category))
        dao.getUncategorizedWithMerchant()
            .filter { MerchantKey.of(it.merchant) == key }
            .forEach { dao.update(it.copy(category = category)) }
    }

    /**
     * One-shot: assigns a category to rows created before auto-categorization
     * existed, or that a since-learned rule now covers. Rows the classifier
     * still can't resolve (unknown merchants, one-off transfers) are left null
     * on purpose — they surface in the "needs review" queue instead of silently
     * landing in a wrong category.
     */
    suspend fun backfillCategories() {
        val learned = ruleDao.getAll()
        dao.getUncategorized().forEach { tx ->
            val category = CategoryClassifier.classify(tx.merchant, tx.rawText, tx.type, learned)
            if (category != null) dao.update(tx.copy(category = category))
        }
    }

    /**
     * Re-reads pushes stored before the parser learned to pull the merchant out
     * of the line under the amount ("Покупка: 1 580 ₸" / "GOOGLE *YouTubePremium")
     * and the balance out of the "Доступно" line. Only rows still missing one of
     * those are touched, so re-running costs nothing.
     */
    /**
     * Marks both halves of a transfer between the user's own cards so the money
     * is not counted as spending on one side and income on the other.
     *
     * Runs over the recent window after every insert: the matching half may
     * arrive either before or after the row being stored.
     */
    suspend fun markSelfTransfers() {
        val candidates = dao.getRecentWithAccount()
        val ownAccounts = SelfTransfer.ownAccountsIn(candidates)
        if (ownAccounts.size < 2) return

        SelfTransfer.findAll(candidates, ownAccounts).forEach { (first, second) ->
            listOf(first, second)
                .filterNot { it.excludedFromStats }
                .forEach { dao.update(it.copy(excludedFromStats = true)) }
        }
    }

    suspend fun backfillNotificationMerchants() {
        dao.getNotificationsWithoutMerchant().forEach { tx ->
            // rawText is "<title> — <body>"; the parser wants the body alone.
            val body = tx.title
                ?.let { tx.rawText.removePrefix("$it — ") }
                ?: tx.rawText
            val parsed = KaspiNotificationParser.parse(tx.title, body)
            val updated = tx.copy(
                merchant = tx.merchant ?: parsed.merchant,
                account = tx.account ?: parsed.account,
                balance = tx.balance ?: parsed.balance
            )
            if (updated != tx) dao.update(updated)
        }
    }

    /**
     * Seeds learned rules from categories the user already assigned by hand, so
     * an upgrade doesn't start asking about long-known merchants from scratch.
     * The most recent choice per merchant wins. Runs only while the rule table
     * is empty.
     */
    suspend fun seedRulesFromHistory() {
        if (ruleDao.getAll().isNotEmpty()) return
        dao.getCategorized()
            .mapNotNull { tx -> MerchantKey.of(tx.merchant)?.let { it to tx } }
            // getCategorized() is ordered oldest first, so the last write per key
            // is the most recent decision.
            .toMap()
            .forEach { (key, tx) ->
                ruleDao.upsert(
                    CategoryRule(
                        merchantKey = key,
                        category = tx.category ?: return@forEach,
                        updatedAt = tx.timestamp
                    )
                )
            }
    }

    /**
     * What the app already knows about a new row's merchant: its category, and
     * — unless the user switched hints off — whether it is usually shared.
     */
    private suspend fun applyRules(tx: Transaction): Transaction {
        val categorized =
            if (tx.category != null) tx
            else tx.copy(
                category = CategoryClassifier.classify(tx.merchant, tx.rawText, tx.type, ruleDao.getAll())
            )
        if (categorized.shared || !sharedHintsEnabled()) return categorized
        val key = MerchantKey.of(categorized.merchant) ?: return categorized
        val hint = sharedRuleDao.getAll().firstOrNull { it.merchantKey == key } ?: return categorized
        return categorized.copy(shared = hint.shared)
    }

    // Backup / restore

    suspend fun exportBackup(): String = Backup.encode(
        Backup.Content(
            transactions = dao.getAllOnce(),
            rules = ruleDao.getAll()
        )
    )

    /**
     * Merges a backup into the current database: operations already present are
     * skipped, so restoring the same file twice changes nothing. Learned rules
     * are overwritten by the backup's version.
     */
    suspend fun importBackup(json: String): Backup.RestoreReport {
        val content = Backup.decode(json)
        val existing = dao.getAllOnce()
        val known = existing.map(Backup::identityOf).toHashSet()
        // An operation edited since the backup was made no longer matches its
        // old fingerprint, but it is still the same operation.
        val knownUuids = existing.map { it.uuid }.toHashSet()

        var added = 0
        var skipped = 0
        content.transactions.forEach { tx ->
            if (tx.uuid in knownUuids || !known.add(Backup.identityOf(tx))) {
                skipped++
            } else {
                dao.insert(tx)
                knownUuids += tx.uuid
                added++
            }
        }
        content.rules.forEach { ruleDao.upsert(it) }

        return Backup.RestoreReport(
            added = added,
            skipped = skipped,
            rules = content.rules.size
        )
    }

    companion object {
        /**
         * Kaspi's duplicate pushes land within a second of each other; a wider
         * window would start swallowing genuinely repeated payments.
         */
        const val NOTIFICATION_DEDUP_WINDOW_MS = 30_000L

        /**
         * How long after the push a receipt for the same payment may be shared.
         * Receipt timestamps are minute-precision, the push is to the second.
         */
        const val RECEIPT_MATCH_WINDOW_MS = 15L * 60 * 1000

        @Volatile
        private var INSTANCE: TransactionRepository? = null

        fun get(context: Context): TransactionRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val db = AppDatabase.getInstance(context)
                    val appContext = context.applicationContext
                    TransactionRepository(
                        dao = db.transactionDao(),
                        ruleDao = db.categoryRuleDao(),
                        sharedRuleDao = db.sharedRuleDao(),
                        pendingDeleteDao = db.pendingDeleteDao(),
                        partnerDao = db.partnerTransactionDao(),
                        sharedHintsEnabled = { SharedHints.enabled(appContext) }
                    ).also { INSTANCE = it }
                }
            }
    }
}
