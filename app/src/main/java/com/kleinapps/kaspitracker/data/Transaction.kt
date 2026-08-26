package com.kleinapps.kaspitracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Operation type derived from the notification text. */
enum class TxType {
    PURCHASE,
    TRANSFER_OUT,
    TRANSFER_IN,
    UNKNOWN;

    /** True for operations that reduce the balance (counted as "spent"). */
    val isSpending: Boolean
        get() = this == PURCHASE || this == TRANSFER_OUT
}

/** Where a record came from — purely informational, shown in Raw Log / History. */
enum class TxSource {
    NOTIFICATION,
    MANUAL,
    RECEIPT
}

/**
 * A single tracked operation: either an intercepted Kaspi notification, a
 * manually added entry, or an imported payment receipt (PDF).
 *
 * Even if parsing fails we still persist the row with [rawText] and a null
 * [amount] / [TxType.UNKNOWN] so nothing is ever lost — the Raw Log screen
 * surfaces these for tuning the parser.
 */
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val title: String?,
    val rawText: String,
    val amount: Double?,
    val type: TxType,
    val merchant: String?,
    val currency: String = "₸",
    val source: TxSource = TxSource.NOTIFICATION,
    /** Null = not yet classified; backfilled on app start and editable by the user. */
    val category: Category? = null,
    /** Source account when known, e.g. "Kaspi Gold" (parsed from receipt "Откуда"). */
    val account: String? = null,
    /**
     * Fingerprint of the notification body, used to drop the copy Kaspi posts
     * alongside every push (group summary + child arrive milliseconds apart and
     * used to be recorded as two separate operations). Null for manual entries
     * and receipts, which have their own duplicate checks.
     */
    val dedupKey: String? = null,
    /**
     * Money left on the card after the operation, from the "Доступно: … ₸" line
     * of a Kaspi push. Only notifications carry it.
     */
    val balance: Double? = null,
    /**
     * Kept in the history but left out of every total.
     *
     * Set for money that never actually left the user's pocket: transfers
     * between their own cards (Kaspi reports those as an expense *and* an
     * income), refunds, and rows corrected by hand.
     */
    val excludedFromStats: Boolean = false
)
