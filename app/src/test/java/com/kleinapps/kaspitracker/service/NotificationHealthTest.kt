package com.kleinapps.kaspitracker.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHealthTest {

    private val hour = 60L * 60 * 1000
    private val day = 24 * hour
    private val now = 1_785_000_000_000L

    /** Pushes every [everyHours] hours, [count] of them, ending just before now. */
    private fun regular(everyHours: Long, count: Int): List<Long> =
        (1..count).map { now - it * everyHours * hour }

    @Test
    fun `too little history falls back to a flat day`() {
        assertEquals(
            NotificationHealth.DEFAULT_SILENCE_MS,
            NotificationHealth.silenceThreshold(regular(everyHours = 4, count = 3))
        )
    }

    @Test
    fun `a heavy user is warned sooner than a light one`() {
        val heavy = NotificationHealth.silenceThreshold(regular(everyHours = 3, count = 40))
        val light = NotificationHealth.silenceThreshold(regular(everyHours = 40, count = 40))
        assertTrue("heavy=$heavy light=$light", heavy < light)
    }

    @Test
    fun `the threshold never drops below the floor`() {
        // Someone paying every ten minutes must not be alarmed after an hour.
        val chatty = NotificationHealth.silenceThreshold(
            (1..60).map { now - it * 10 * 60 * 1000L }
        )
        assertTrue(chatty >= NotificationHealth.MIN_SILENCE_MS)
    }

    @Test
    fun `the threshold never exceeds the ceiling`() {
        val rare = NotificationHealth.silenceThreshold(
            (1..20).map { now - it * 30 * day }
        )
        assertEquals(NotificationHealth.MAX_SILENCE_MS, rare)
    }

    @Test
    fun `a quiet weekend is not a fault when the history has quiet weekends`() {
        // Mostly a push every few hours, but with the occasional day-and-a-half
        // gap the user's life already produces. p90 has to absorb those, so a
        // 20-hour silence stays unremarkable.
        val history = buildList {
            var moment = now
            repeat(40) { index ->
                moment -= if (index % 7 == 0) 30 * hour else 4 * hour
                add(moment)
            }
        }
        assertFalse(
            NotificationHealth.isSilent(
                lastSeenAt = now - 20 * hour,
                history = history,
                now = now
            )
        )
    }

    @Test
    fun `steady spending going quiet for a day is a fault`() {
        // No long gaps in this history at all, so a whole day of nothing is
        // genuinely out of character.
        assertTrue(
            NotificationHealth.isSilent(
                lastSeenAt = now - 26 * hour,
                history = regular(everyHours = 4, count = 40),
                now = now
            )
        )
    }

    @Test
    fun `a week of silence from a daily user is a fault`() {
        val history = regular(everyHours = 12, count = 40)
        assertTrue(
            NotificationHealth.isSilent(
                lastSeenAt = now - 7 * day,
                history = history,
                now = now
            )
        )
    }

    @Test
    fun `a fresh install is never reported as broken`() {
        assertFalse(NotificationHealth.isSilent(null, emptyList(), now))
        assertFalse(NotificationHealth.isSilent(0L, emptyList(), now))
    }

    @Test
    fun `hours since is whole hours`() {
        assertEquals(3, NotificationHealth.hoursSince(now - 3 * hour - 59 * 60 * 1000, now))
        assertEquals(0, NotificationHealth.hoursSince(now - 30 * 60 * 1000, now))
    }
}
