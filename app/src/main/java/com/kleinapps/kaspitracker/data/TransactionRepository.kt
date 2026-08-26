package com.kleinapps.kaspitracker.data

import android.content.Context
import com.kleinapps.kaspitracker.parser.KaspiNotificationParser
import com.kleinapps.kaspitracker.service.BudgetAlerts
import com.kleinapps.kaspitracker.util.startOfMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/**
 * Told about the running total of a budgeted category right after money was
 * recorded in it. Kept as an interface so the data layer stays free of Android
 * notification APIs (and so tests can assert on it).
 */
fun interface BudgetWatcher {
    fun onSpendingRecorded(budget: Budget, spentThisMonth: Double, monthKey: String)
}

/** Thin wrapper over the DAOs. Single source of truth for the whole app. */
class TransactionRepository(
    private val dao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val ruleDao: CategoryRuleDao,
    private val budgetWatcher: BudgetWatcher? = null
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

    fun observeSpentBetween(from: Long, to: Long): Flow<Double> = dao.observeSpentBetween(from, to)

    fun observeIncomeBetween(from: Long, to: Long): Flow<Double> = dao.observeIncomeBetween(from, to)

    fun observeBetween(from: Long, to: Long): Flow<List<Transaction>> = dao.observeBetween(from, to)

    fun observeAccounts(): Flow<List<String>> = dao.observeAccounts()

    /** Rows still waiting for the user to pick a category — surfaced on Home. */
    fun observeUncategorized(): Flow<List<Transaction>> = dao.observeUncategorized()

    /** Learned merchant -> category rules, for the settings/debug UI. */
    fun observeCategoryRules(): Flow<List<CategoryRule>> = ruleDao.observeAll()

    /** Last known balance of every card, from the "Доступно" line of pushes. */
    fun observeBalances(): Flow<List<AccountBalance>> = dao.observeBalances()

    /** Recent balance readings per card, oldest first, for the card trend line. */
    fun observeBalanceHistory(): Flow<Map<String, List<Double>>> =
        dao.observeBalancePoints().map { points ->
            points
                .groupBy { it.account }
                .mapValues { (_, readings) ->
                    readings.sortedBy { it.timestamp }.takeLast(12).map { it.balance }
                }
        }

    /** Recurring payments (subscriptions, memberships) found in the history. */
    fun observeSubscriptions(): Flow<List<Subscription>> =
        dao.observeSpendings().map { SubscriptionDetector.detect(it) }

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
        val stored = autoCategorize(transaction)
        val id = dao.insert(stored)
        reportBudget(stored)
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
        val stored = autoCategorize(transaction)
        dao.insert(stored).also { reportBudget(stored) }
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
        val stored = autoCategorize(transaction)
        dao.insert(stored).also { reportBudget(stored) }
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
    }

    /** Category picked from the review queue — same learning as [update]. */
    suspend fun assignCategory(transaction: Transaction, category: Category) {
        dao.update(transaction.copy(category = category))
        learn(transaction.merchant, category)
    }

    suspend fun delete(transaction: Transaction) = dao.delete(transaction)

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

    // Budgets
    fun observeBudgets(): Flow<List<Budget>> = budgetDao.observeAll()

    suspend fun setBudget(budget: Budget) = budgetDao.upsert(budget)

    suspend fun removeBudget(budget: Budget) = budgetDao.delete(budget)

    /**
     * Hands the category's running month total to the [budgetWatcher] whenever
     * money lands in a category that has a limit set.
     */
    private suspend fun reportBudget(tx: Transaction) {
        val watcher = budgetWatcher ?: return
        val category = tx.category ?: return
        if (!tx.type.isSpending || tx.amount == null) return

        val monthStart = startOfMonth()
        if (tx.timestamp < monthStart) return

        val budget = budgetDao.getAllOnce().firstOrNull { it.category == category } ?: return
        val spent = dao.sumSpentInCategory(category, monthStart, Long.MAX_VALUE)
        watcher.onSpendingRecorded(budget, spent, monthKey(monthStart))
    }

    private fun monthKey(monthStart: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = monthStart }
        return "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.MONTH) + 1}"
    }

    private suspend fun autoCategorize(tx: Transaction): Transaction =
        if (tx.category != null) tx
        else tx.copy(
            category = CategoryClassifier.classify(tx.merchant, tx.rawText, tx.type, ruleDao.getAll())
        )

    // Backup / restore

    suspend fun exportBackup(): String = Backup.encode(
        Backup.Content(
            transactions = dao.getAllOnce(),
            budgets = budgetDao.getAllOnce(),
            rules = ruleDao.getAll()
        )
    )

    /**
     * Merges a backup into the current database: operations already present are
     * skipped, so restoring the same file twice changes nothing. Budgets and
     * learned rules are overwritten by the backup's version.
     */
    suspend fun importBackup(json: String): Backup.RestoreReport {
        val content = Backup.decode(json)
        val known = dao.getAllOnce().map(Backup::identityOf).toHashSet()

        var added = 0
        var skipped = 0
        content.transactions.forEach { tx ->
            if (!known.add(Backup.identityOf(tx))) {
                skipped++
            } else {
                dao.insert(tx)
                added++
            }
        }
        content.budgets.forEach { budgetDao.upsert(it) }
        content.rules.forEach { ruleDao.upsert(it) }

        return Backup.RestoreReport(
            added = added,
            skipped = skipped,
            budgets = content.budgets.size,
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
                        budgetDao = db.budgetDao(),
                        ruleDao = db.categoryRuleDao(),
                        budgetWatcher = { budget, spent, monthKey ->
                            BudgetAlerts.notifyIfNeeded(appContext, budget, spent, monthKey)
                        }
                    ).also { INSTANCE = it }
                }
            }
    }
}
