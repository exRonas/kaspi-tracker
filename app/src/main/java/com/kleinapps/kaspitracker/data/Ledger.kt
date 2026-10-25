package com.kleinapps.kaspitracker.data

/** Whose operation a [LedgerEntry] is. */
enum class Owner { ME, PARTNER }

/** Which money the screens are showing: mine, the partner's, or what we share. */
enum class ViewScope { ME, PARTNER, TOGETHER }

/** One operation as a screen sees it, with whose it is. */
data class LedgerEntry(val tx: Transaction, val owner: Owner)

/**
 * Money that really left the pocket. Every total in the app goes through this
 * one rule, so a transfer between own cards (excluded) can't sneak back into
 * one screen while the others leave it out.
 */
val Transaction.isCountedSpending: Boolean
    get() = amount != null && !excludedFromStats && type.isSpending

/** Money that really came in, by the same rule as [isCountedSpending]. */
val Transaction.isCountedIncome: Boolean
    get() = amount != null && !excludedFromStats && type == TxType.TRANSFER_IN

/**
 * The operations behind one view, newest first. "Together" is only what either
 * of us marked as shared — personal spending stays in its owner's own view.
 */
fun ledgerFor(scope: ViewScope, mine: List<Transaction>, partner: List<Transaction>): List<LedgerEntry> {
    val entries = when (scope) {
        ViewScope.ME -> mine.map { LedgerEntry(it, Owner.ME) }
        ViewScope.PARTNER -> partner.map { LedgerEntry(it, Owner.PARTNER) }
        ViewScope.TOGETHER ->
            mine.filter { it.shared }.map { LedgerEntry(it, Owner.ME) } +
                partner.filter { it.shared }.map { LedgerEntry(it, Owner.PARTNER) }
    }
    return entries.sortedByDescending { it.tx.timestamp }
}

/** Newest balance reading of every card, from the "Доступно" line of pushes. */
fun latestBalances(txs: List<Transaction>): List<AccountBalance> =
    balanceReadings(txs)
        .groupBy { it.account }
        .map { (_, readings) -> readings.maxBy { it.timestamp } }

/**
 * Recent readings per card, oldest first: the last [BALANCE_POINTS] readings
 * overall, at most [POINTS_PER_CARD] per card — what the trend line draws.
 */
fun balanceHistory(txs: List<Transaction>): Map<String, List<Double>> =
    balanceReadings(txs)
        .sortedByDescending { it.timestamp }
        .take(BALANCE_POINTS)
        .groupBy { it.account }
        .mapValues { (_, readings) ->
            readings.sortedBy { it.timestamp }.takeLast(POINTS_PER_CARD).map { it.balance }
        }

private fun balanceReadings(txs: List<Transaction>): List<AccountBalance> =
    txs.mapNotNull { tx ->
        val account = tx.account ?: return@mapNotNull null
        val balance = tx.balance ?: return@mapNotNull null
        AccountBalance(account, balance, tx.timestamp)
    }

private const val BALANCE_POINTS = 60
private const val POINTS_PER_CARD = 12
