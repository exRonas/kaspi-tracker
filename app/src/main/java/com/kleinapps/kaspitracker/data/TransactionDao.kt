package com.kleinapps.kaspitracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Last known balance of one card, from the newest push that carried one. */
data class AccountBalance(
    val account: String,
    val balance: Double,
    val timestamp: Long
)

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Transaction>

    /** Records which version of a row the cloud now holds. */
    @Query("UPDATE transactions SET syncedHash = :hash WHERE uuid = :uuid")
    suspend fun markSynced(uuid: String, hash: Int)

    /** Forgets what was sent — after unpairing everything counts as unsent again. */
    @Query("UPDATE transactions SET syncedHash = NULL")
    suspend fun clearSyncState()

    /** Every row, once — used by backup export and restore matching. */
    @Query("SELECT * FROM transactions ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<Transaction>

    /**
     * Copies of the same push already stored around [from]..[to]. Kaspi posts the
     * group summary and the notification itself milliseconds apart, and Android
     * re-delivers a notification whenever it is updated.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE dedupKey = :dedupKey AND timestamp BETWEEN :from AND :to
        ORDER BY timestamp ASC LIMIT 1
        """
    )
    suspend fun findByDedupKey(dedupKey: String, from: Long, to: Long): Transaction?

    /** Rows the user has already categorized — the seed for learned rules. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE category IS NOT NULL AND merchant IS NOT NULL
        ORDER BY timestamp ASC
        """
    )
    suspend fun getCategorized(): List<Transaction>

    /**
     * Pushes stored before the parser could read the merchant line or the
     * "Доступно" balance out of them.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE source = 'NOTIFICATION' AND (merchant IS NULL OR balance IS NULL)
        """
    )
    suspend fun getNotificationsWithoutMerchant(): List<Transaction>

    /** Uncategorized rows for one merchant — filled in when a rule is learned. */
    @Query("SELECT * FROM transactions WHERE category IS NULL AND merchant IS NOT NULL")
    suspend fun getUncategorizedWithMerchant(): List<Transaction>

    /** Rows that predate auto-categorization — backfilled once on app start. */
    @Query("SELECT * FROM transactions WHERE category IS NULL")
    suspend fun getUncategorized(): List<Transaction>

    /**
     * Live queue of rows still waiting for the user to pick a category.
     * Amount-less rows (Kaspi push with no parseable sum) aren't real
     * operations, so they never enter the queue.
     */
    @Query("SELECT * FROM transactions WHERE category IS NULL AND amount IS NOT NULL ORDER BY timestamp DESC")
    fun observeUncategorized(): Flow<List<Transaction>>

    @Query("SELECT DISTINCT account FROM transactions WHERE account IS NOT NULL ORDER BY account")
    fun observeAccounts(): Flow<List<String>>

    /**
     * Last known balance of every card. SQLite returns the row that produced
     * MAX(timestamp) for the bare columns, which is exactly the newest push.
     */
    @Query(
        """
        SELECT account AS account, balance AS balance, MAX(timestamp) AS timestamp
        FROM transactions
        WHERE balance IS NOT NULL AND account IS NOT NULL
        GROUP BY account
        """
    )
    fun observeBalances(): Flow<List<AccountBalance>>

    /**
     * Recent balance readings, newest first — the raw material for the trend
     * line on a card. Capped because the card only ever draws a dozen points.
     */
    @Query(
        """
        SELECT account AS account, balance AS balance, timestamp AS timestamp
        FROM transactions
        WHERE balance IS NOT NULL AND account IS NOT NULL
        ORDER BY timestamp DESC LIMIT 60
        """
    )
    fun observeBalancePoints(): Flow<List<AccountBalance>>

    /**
     * An already recorded push for the same payment a receipt is being imported
     * for: same amount, a few minutes apart. Kaspi sends the push instantly and
     * the receipt is shared later, so both describe one operation.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE source = 'NOTIFICATION' AND amount = :amount
          AND timestamp BETWEEN :from AND :to
        ORDER BY ABS(timestamp - :timestamp) ASC LIMIT 1
        """
    )
    suspend fun findNotificationForReceipt(
        amount: Double,
        timestamp: Long,
        from: Long,
        to: Long
    ): Transaction?

    /**
     * When Kaspi pushes arrived, newest first — the watchdog derives the user's
     * normal rhythm from these to know how long a silence is too long.
     */
    @Query(
        """
        SELECT timestamp FROM transactions
        WHERE source = 'NOTIFICATION'
        ORDER BY timestamp DESC LIMIT 200
        """
    )
    suspend fun getNotificationTimestamps(): List<Long>

    /**
     * Transfers that carry an account, newest first — the pool the self-transfer
     * matcher works over. Capped: pairs are minutes apart, never months.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE amount IS NOT NULL AND account IS NOT NULL
        ORDER BY timestamp DESC LIMIT 400
        """
    )
    suspend fun getRecentWithAccount(): List<Transaction>

    /** Spending rows of one merchant, oldest first — used to spot subscriptions. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE amount IS NOT NULL AND excludedFromStats = 0
          AND type IN ('PURCHASE', 'TRANSFER_OUT')
        ORDER BY timestamp ASC
        """
    )
    fun observeSpendings(): Flow<List<Transaction>>

    /** Detects re-import of the same receipt (same moment, amount and source). */
    @Query(
        """
        SELECT COUNT(*) FROM transactions
        WHERE source = 'RECEIPT' AND timestamp = :timestamp
          AND (amount = :amount OR (amount IS NULL AND :amount IS NULL))
        """
    )
    suspend fun countReceiptDuplicates(timestamp: Long, amount: Double?): Int

    @Query("DELETE FROM transactions")
    suspend fun clearAll()

    /** Every row, including amount-less ones — used only by the Raw Log/debug screen. */
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Transaction>>

    /** Same as [observeAll] but drops rows with no parseable amount — not real operations. */
    @Query("SELECT * FROM transactions WHERE amount IS NOT NULL ORDER BY timestamp DESC")
    fun observeAllWithAmount(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE amount IS NOT NULL ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Transaction>>

    /** Spending operations (purchases + outgoing transfers) within a time range. */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE amount IS NOT NULL
          AND excludedFromStats = 0
          AND type IN ('PURCHASE', 'TRANSFER_OUT')
          AND timestamp BETWEEN :from AND :to
        """
    )
    fun observeSpentBetween(from: Long, to: Long): Flow<Double>

    @Query(
        """
        SELECT * FROM transactions
        WHERE amount IS NOT NULL AND timestamp BETWEEN :from AND :to
        ORDER BY timestamp ASC
        """
    )
    fun observeBetween(from: Long, to: Long): Flow<List<Transaction>>

    /** Incoming transfers within a time range (money received). */
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM transactions
        WHERE amount IS NOT NULL
          AND excludedFromStats = 0
          AND type = 'TRANSFER_IN'
          AND timestamp BETWEEN :from AND :to
        """
    )
    fun observeIncomeBetween(from: Long, to: Long): Flow<Double>
}
