package com.kleinapps.kaspitracker.util

import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TxSource
import com.kleinapps.kaspitracker.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class StatsTest {

    private val zone = TimeZone.getTimeZone("Asia/Almaty")

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun spend(amount: Double?, at: Long, excluded: Boolean = false) = Transaction(
        timestamp = at, title = null, rawText = "", amount = amount,
        type = TxType.PURCHASE, merchant = "Magnum", source = TxSource.MANUAL,
        excludedFromStats = excluded
    )

    private fun income(amount: Double, at: Long) =
        spend(amount, at).copy(type = TxType.TRANSFER_IN)

    @Test
    fun `excluded and amount-less rows are not counted`() {
        val txs = listOf(
            spend(1_000.0, at(2026, 1, 10)),
            spend(500.0, at(2026, 1, 11), excluded = true),
            spend(null, at(2026, 1, 12))
        )
        assertEquals(1_000.0, Stats.year(txs, 2026, at(2026, 5, 15), zone).months[0].spent, 0.001)
    }

    @Test
    fun `year splits spending and income by calendar month`() {
        val txs = listOf(spend(1_000.0, at(2026, 1, 10)), income(500.0, at(2026, 3, 5)))
        val year = Stats.year(txs, 2026, at(2026, 5, 15), zone)

        assertEquals(12, year.months.size)
        assertEquals(1_000.0, year.months[0].spent, 0.001)
        assertEquals(500.0, year.months[2].income, 0.001)
        assertEquals(1_000.0, year.spent, 0.001)
        assertEquals(500.0, year.income, 0.001)
    }

    @Test
    fun `months after now are future and have zero totals`() {
        val year = Stats.year(emptyList(), 2026, at(2026, 5, 15), zone)

        assertFalse(year.months[4].isFuture)
        assertTrue(year.months.drop(5).all { it.isFuture && it.spent == 0.0 })
    }

    @Test
    fun `average month ignores empty and future months`() {
        val txs = listOf(spend(1_000.0, at(2026, 1, 10)), spend(3_000.0, at(2026, 3, 10)))
        assertEquals(2_000.0, Stats.year(txs, 2026, at(2026, 5, 15), zone).averageMonthSpent!!, 0.001)
    }

    @Test
    fun `average month is null for a year with nothing in it`() {
        assertNull(Stats.year(emptyList(), 2026, at(2026, 5, 15), zone).averageMonthSpent)
    }

    @Test
    fun `priciest month is the one with most spending`() {
        val txs = listOf(spend(1_000.0, at(2026, 1, 10)), spend(3_000.0, at(2026, 3, 10)))
        assertEquals(2, Stats.year(txs, 2026, at(2026, 5, 15), zone).priciestMonth!!.month)
    }

    @Test
    fun `operation at 23-59 on Jan 31 belongs to January`() {
        val txs = listOf(
            spend(100.0, at(2026, 1, 31, hour = 23, minute = 59)),
            spend(200.0, at(2026, 2, 1, hour = 0, minute = 0))
        )
        val months = Stats.year(txs, 2026, at(2026, 5, 15), zone).months

        assertEquals(100.0, months[0].spent, 0.001)
        assertEquals(200.0, months[1].spent, 0.001)
    }

    @Test
    fun `years include current year even without operations`() {
        val txs = listOf(spend(100.0, at(2025, 6, 1)))
        assertEquals(listOf(2025, 2026), Stats.years(txs, at(2026, 5, 15), zone))
    }

    @Test
    fun `per day divides by days since the first operation inclusive`() {
        val txs = listOf(spend(400.0, at(2026, 5, 1, hour = 10)), spend(600.0, at(2026, 5, 7)))
        val stats = Stats.overall(txs, at(2026, 5, 10, hour = 15), zone)!!

        assertEquals(at(2026, 5, 1, hour = 0), stats.firstDay)
        assertEquals(10, stats.days)
        assertEquals(100.0, stats.perDay, 0.001)
        assertEquals(700.0, stats.perWeek, 0.001)
        assertEquals(3_043.75, stats.perMonth, 0.001)
    }

    @Test
    fun `weekday averages divide by how many such weekdays passed`() {
        // 4 and 11 May 2026 are Mondays; "now" is Tuesday the 12th.
        val txs = listOf(spend(300.0, at(2026, 5, 4)), spend(300.0, at(2026, 5, 11)))
        val stats = Stats.overall(txs, at(2026, 5, 12), zone)!!

        assertEquals(7, stats.weekdayAverages.size)
        assertEquals(300.0, stats.weekdayAverages[0], 0.001)
        assertEquals(0.0, stats.weekdayAverages[1], 0.001)
    }

    @Test
    fun `last weeks are twelve, oldest first, ending with this week`() {
        val txs = listOf(spend(250.0, at(2026, 5, 5)))
        val weeks = Stats.overall(txs, at(2026, 5, 12), zone)!!.lastWeeks

        assertEquals(12, weeks.size)
        assertEquals(at(2026, 5, 11, hour = 0), weeks.last().start)
        assertEquals(at(2026, 5, 4, hour = 0), weeks[10].start)
        assertEquals(250.0, weeks[10].spent, 0.001)
    }

    @Test
    fun `priciest day and days without spending`() {
        val txs = listOf(
            spend(300.0, at(2026, 5, 4, hour = 9)),
            spend(200.0, at(2026, 5, 4, hour = 20)),
            spend(100.0, at(2026, 5, 6))
        )
        val stats = Stats.overall(txs, at(2026, 5, 7), zone)!!

        assertEquals(at(2026, 5, 4, hour = 0), stats.priciestDay!!.start)
        assertEquals(500.0, stats.priciestDay!!.spent, 0.001)
        assertEquals(2, stats.daysWithoutSpending)
        assertEquals(600.0, stats.totalSpent, 0.001)
    }

    @Test
    fun `income counts toward totals but not spending`() {
        val txs = listOf(spend(100.0, at(2026, 5, 4)), income(5_000.0, at(2026, 5, 5)))
        val stats = Stats.overall(txs, at(2026, 5, 5), zone)!!

        assertEquals(5_000.0, stats.totalIncome, 0.001)
        assertEquals(100.0, stats.totalSpent, 0.001)
    }

    @Test
    fun `overall is null with no counted operations`() {
        val txs = listOf(spend(500.0, at(2026, 5, 4), excluded = true))
        assertNull(Stats.overall(txs, at(2026, 5, 7), zone))
    }
}
