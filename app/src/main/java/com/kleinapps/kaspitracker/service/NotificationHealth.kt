package com.kleinapps.kaspitracker.service

import kotlin.math.min

/**
 * Decides whether the notification listener has gone quiet for the wrong
 * reasons.
 *
 * The failure this guards against is silent: Samsung (and other OEM ROMs) put
 * the process to sleep, Kaspi pushes stop arriving, and the app keeps looking
 * perfectly healthy — the user only finds out days later that nothing was
 * recorded.
 *
 * The threshold is derived from the user's own rhythm rather than fixed: a
 * person who pays for something twice a day should be warned within hours,
 * while someone who uses the card twice a week should not be nagged every
 * evening.
 */
object NotificationHealth {

    private const val HOUR_MS = 60L * 60 * 1000
    private const val DAY_MS = 24 * HOUR_MS

    /** Never warn before this much silence, however chatty the history is. */
    const val MIN_SILENCE_MS = 12 * HOUR_MS

    /** Never wait longer than this, however rare the pushes are. */
    const val MAX_SILENCE_MS = 3 * DAY_MS

    /** Not enough history to know the rhythm — fall back to a flat day. */
    const val DEFAULT_SILENCE_MS = DAY_MS

    private const val MIN_SAMPLES = 5

    /**
     * How long a gap has to be before it means something is broken.
     *
     * Twice the 90th percentile of past gaps: p90 already covers the quiet
     * stretches (a weekend without spending), and doubling it leaves room for
     * an unusually slow week before the app starts crying wolf.
     */
    fun silenceThreshold(timestamps: List<Long>): Long {
        val gaps = timestamps.sorted().zipWithNext { previous, next -> next - previous }
            .filter { it > 0 }
        if (gaps.size < MIN_SAMPLES) return DEFAULT_SILENCE_MS

        return (percentile(gaps, 0.9) * 2)
            .coerceIn(MIN_SILENCE_MS, MAX_SILENCE_MS)
    }

    /**
     * True when [lastSeenAt] is old enough to be suspicious. A listener that has
     * never seen a push (`lastSeenAt == null`) is not reported: that is a fresh
     * install, not a fault.
     */
    fun isSilent(
        lastSeenAt: Long?,
        history: List<Long>,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        if (lastSeenAt == null || lastSeenAt <= 0) return false
        return now - lastSeenAt > silenceThreshold(history)
    }

    /** Nearest-rank percentile; the list is small enough that sorting is free. */
    private fun percentile(values: List<Long>, fraction: Double): Long {
        val sorted = values.sorted()
        val rank = Math.ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    /** Whole hours since the last push, for the wording of the warning. */
    fun hoursSince(lastSeenAt: Long, now: Long = System.currentTimeMillis()): Int =
        min((now - lastSeenAt) / HOUR_MS, Int.MAX_VALUE.toLong()).toInt()
}
