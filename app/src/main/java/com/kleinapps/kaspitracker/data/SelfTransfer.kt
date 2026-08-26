package com.kleinapps.kaspitracker.data

import kotlin.math.abs

/**
 * Recognizes money moved between the user's own cards.
 *
 * Kaspi reports such a move as two independent pushes — one leaving Gold, one
 * landing on Deposit — so without this both halves are counted: the month looks
 * like it had an extra expense *and* an extra income, which throws off the
 * headline total, the forecast, the budgets and the category breakdown alike.
 *
 * The pair is recognized by the shape of the two records rather than by any
 * wording, because the pushes carry no marker saying they belong together.
 */
object SelfTransfer {

    /**
     * How far apart the two halves may be. The second push follows the first
     * almost immediately; a couple of minutes covers a slow network without
     * starting to swallow genuinely unrelated payments of the same size.
     */
    const val WINDOW_MS = 3 * 60_000L

    /** True for money leaving the account (purchase or outgoing transfer). */
    private val Transaction.isOutgoing: Boolean
        get() = type.isSpending

    /**
     * The other half of [tx] among [candidates], or null when this is an
     * ordinary payment.
     *
     * A match needs the same amount, opposite directions, two *different*
     * accounts that are both known to be the user's, and a small time gap.
     */
    fun matchIn(
        tx: Transaction,
        candidates: List<Transaction>,
        ownAccounts: Set<String>
    ): Transaction? {
        val amount = tx.amount ?: return null
        val account = tx.account ?: return null
        if (account !in ownAccounts) return null

        return candidates.firstOrNull { other ->
            other.id != tx.id &&
                other.amount == amount &&
                other.isOutgoing != tx.isOutgoing &&
                other.account != null &&
                other.account != account &&
                other.account in ownAccounts &&
                abs(other.timestamp - tx.timestamp) <= WINDOW_MS
        }
    }

    /**
     * Every self-transfer inside [transactions], as pairs. Used by the one-shot
     * pass over history; new operations are matched one at a time on insert.
     */
    fun findAll(
        transactions: List<Transaction>,
        ownAccounts: Set<String>
    ): List<Pair<Transaction, Transaction>> {
        val ordered = transactions.sortedBy { it.timestamp }
        val paired = HashSet<Long>()
        val result = mutableListOf<Pair<Transaction, Transaction>>()

        ordered.forEach { tx ->
            if (tx.id in paired) return@forEach
            val other = matchIn(
                tx = tx,
                candidates = ordered.filterNot { it.id in paired },
                ownAccounts = ownAccounts
            ) ?: return@forEach
            paired += tx.id
            paired += other.id
            result += tx to other
        }
        return result
    }

    /**
     * Cards the user actually owns, learned from the operations themselves:
     * an account name only ever appears on a push addressed to one of their own
     * cards, so every distinct account seen is one of theirs.
     */
    fun ownAccountsIn(transactions: List<Transaction>): Set<String> =
        transactions.mapNotNull { it.account }.toSet()
}
