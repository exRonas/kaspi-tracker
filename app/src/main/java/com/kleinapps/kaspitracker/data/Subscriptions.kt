package com.kleinapps.kaspitracker.data

import kotlin.math.abs

/** How often a recurring payment comes back. */
enum class Cadence(val label: String, val perMonth: Double) {
    WEEKLY("каждую неделю", 4.33),
    MONTHLY("раз в месяц", 1.0),
    YEARLY("раз в год", 1.0 / 12)
}

/**
 * A payment that keeps coming back to the same merchant.
 *
 * [confirmed] is true when the repetition was actually observed (two or more
 * payments a cadence apart). A single payment to a merchant whose name is a
 * known subscription service is still reported, but unconfirmed — there is not
 * enough history to prove the cadence yet.
 */
data class Subscription(
    val merchant: String,
    val merchantKey: String,
    val amount: Double,
    val category: Category?,
    val cadence: Cadence?,
    val payments: Int,
    val lastPaid: Long,
    val nextExpected: Long?,
    val confirmed: Boolean
) {
    /** What this costs per month, for the total on the subscriptions screen. */
    val monthlyCost: Double
        get() = amount * (cadence?.perMonth ?: 1.0)
}

/**
 * Finds recurring payments in the spending history.
 *
 * Two payments to one merchant count as a subscription when they are a whole
 * cadence apart (give or take a few days — Kaspi charges drift) and the amounts
 * are within [AMOUNT_TOLERANCE] of each other; a service that raised its price
 * is still the same subscription.
 */
object SubscriptionDetector {

    private const val DAY = 24L * 60 * 60 * 1000

    /** Accepted spacing between two charges of the same subscription, in days. */
    private val CADENCE_WINDOWS = mapOf(
        Cadence.WEEKLY to 6..8,
        Cadence.MONTHLY to 25..35,
        Cadence.YEARLY to 355..375
    )

    /**
     * How many charges it takes to believe a cadence.
     *
     * Weekly needs three, because two grocery runs a week apart for a similar
     * sum are not a subscription — that pattern is just how someone shops, and
     * with two payments it is indistinguishable from a real weekly charge.
     */
    private val REQUIRED_PAYMENTS = mapOf(
        Cadence.WEEKLY to 3,
        Cadence.MONTHLY to 2,
        Cadence.YEARLY to 2
    )

    private const val AMOUNT_TOLERANCE = 0.20

    /** Weekly charges must also be steadier in price than monthly ones. */
    private const val WEEKLY_AMOUNT_TOLERANCE = 0.10

    /** Merchants that are subscriptions even before a second charge shows up. */
    private val KNOWN_SERVICES = listOf(
        "yandex plus", "yandex.plus", "youtube", "google", "netflix", "spotify",
        "apple", "steam", "playstation", "icloud", "dropbox", "chatgpt", "openai",
        "claude", "anthropic", "megogo", "ivi", "okko", "telegram premium",
        "годовое обслуживание", "подписк", "abonement", "абонемент"
    )

    fun detect(
        spendings: List<Transaction>,
        now: Long = System.currentTimeMillis()
    ): List<Subscription> =
        spendings
            .filter { it.amount != null && it.merchant != null }
            .groupBy { MerchantKey.of(it.merchant) }
            .mapNotNull { (key, txs) -> if (key == null) null else fromHistory(key, txs, now) }
            .sortedByDescending { it.monthlyCost }

    private fun fromHistory(key: String, txs: List<Transaction>, now: Long): Subscription? {
        val ordered = txs.sortedBy { it.timestamp }
        val last = ordered.last()
        val cadence = detectCadence(ordered)

        val known = KNOWN_SERVICES.any { service ->
            listOfNotNull(last.merchant, last.rawText).any { it.contains(service, ignoreCase = true) }
        }
        if (cadence == null && !known) return null

        // The newest charge is the price to quote — services change their fee.
        val amount = last.amount ?: return null
        return Subscription(
            merchant = last.merchant ?: key,
            merchantKey = key,
            amount = amount,
            category = last.category,
            cadence = cadence,
            payments = ordered.size,
            lastPaid = last.timestamp,
            // A date already in the past is not a forecast — the charge either
            // happened and is not recorded yet, or the subscription is over.
            nextExpected = cadence
                ?.let { last.timestamp + periodMillis(it) }
                ?.takeIf { it > now },
            confirmed = cadence != null
        )
    }

    /**
     * The cadence the two most recent charges agree on. Older gaps are ignored
     * on purpose: a subscription that was cancelled and resubscribed months
     * later should be read from its current rhythm.
     */
    private fun detectCadence(ordered: List<Transaction>): Cadence? {
        if (ordered.size < 2) return null
        // Only the tail matters: a subscription cancelled and taken up again
        // months later should be read from its current rhythm.
        val recent = ordered.takeLast(4)

        return CADENCE_WINDOWS.entries
            .firstOrNull { (cadence, window) -> holds(recent, cadence, window) }
            ?.key
    }

    /**
     * True when the last [REQUIRED_PAYMENTS] charges are all one [window] apart
     * and cost about the same.
     */
    private fun holds(recent: List<Transaction>, cadence: Cadence, window: IntRange): Boolean {
        val needed = REQUIRED_PAYMENTS.getValue(cadence)
        if (recent.size < needed) return false
        val tolerance =
            if (cadence == Cadence.WEEKLY) WEEKLY_AMOUNT_TOLERANCE else AMOUNT_TOLERANCE

        val streak = recent.takeLast(needed)
        return streak.zipWithNext().all { (previous, current) ->
            val gapDays = ((current.timestamp - previous.timestamp) / DAY).toInt()
            gapDays in window && amountsMatch(previous.amount, current.amount, tolerance)
        }
    }

    private fun amountsMatch(a: Double?, b: Double?, tolerance: Double): Boolean {
        if (a == null || b == null) return false
        val reference = maxOf(a, b)
        return reference > 0 && abs(a - b) / reference <= tolerance
    }

    private fun periodMillis(cadence: Cadence): Long = when (cadence) {
        Cadence.WEEKLY -> 7 * DAY
        Cadence.MONTHLY -> 30 * DAY
        Cadence.YEARLY -> 365 * DAY
    }
}
