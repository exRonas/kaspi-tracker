package com.kleinapps.kaspitracker.util

import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.isCountedIncome
import com.kleinapps.kaspitracker.data.isCountedSpending
import java.util.Calendar
import java.util.TimeZone

/** One calendar month of a year; [month] is 0 for January, [end] is exclusive. */
data class MonthTotal(
    val month: Int,
    val start: Long,
    val end: Long,
    val spent: Double,
    val income: Double,
    val isFuture: Boolean
)

data class YearStats(
    val year: Int,
    val months: List<MonthTotal>,
    val spent: Double,
    val income: Double,
    /** Mean spending of the months that already happened and had any money in them. */
    val averageMonthSpent: Double?,
    val priciestMonth: MonthTotal?
)

/** Spending of one day or week, starting at [start]. */
data class PeriodTotal(val start: Long, val spent: Double)

data class OverallStats(
    /** Start of the day of the first counted operation — what the averages run from. */
    val firstDay: Long,
    /** Calendar days from [firstDay] to today, both included. */
    val days: Int,
    val perDay: Double,
    val perWeek: Double,
    val perMonth: Double,
    /** Average spending on each weekday, Monday first. */
    val weekdayAverages: List<Double>,
    /** The last [WEEKS] weeks (Monday to Sunday), oldest first; the last one is this week. */
    val lastWeeks: List<PeriodTotal>,
    val priciestDay: PeriodTotal?,
    val daysWithoutSpending: Int,
    val totalSpent: Double,
    val totalIncome: Double
)

/**
 * Year-by-month and all-time numbers for the Statistics tab. Pure functions of
 * the operation list, in the given time zone, so month and week boundaries can
 * be tested without the device clock.
 *
 * The averages run from the first operation rather than from January 1st: a
 * history that starts in October would otherwise look like a very frugal year.
 */
object Stats {

    const val WEEKS = 12

    /** Average days in a month (365.25 / 12), for the "per month" average. */
    private const val DAYS_PER_MONTH = 30.4375

    fun years(txs: List<Transaction>, now: Long, zone: TimeZone = TimeZone.getDefault()): List<Int> {
        val calendar = Calendar.getInstance(zone)
        fun yearOf(millis: Long) = calendar.apply { timeInMillis = millis }.get(Calendar.YEAR)
        return (txs.filter { it.isCounted }.map { yearOf(it.timestamp) } + yearOf(now))
            .distinct()
            .sorted()
    }

    fun year(txs: List<Transaction>, year: Int, now: Long, zone: TimeZone = TimeZone.getDefault()): YearStats {
        val months = (0 until 12).map { month ->
            val start = monthStart(year, month, zone)
            val end = monthStart(if (month == 11) year + 1 else year, (month + 1) % 12, zone)
            val inMonth = txs.filter { it.timestamp in start until end }
            MonthTotal(
                month = month,
                start = start,
                end = end,
                spent = inMonth.filter { it.isCountedSpending }.sumOf { it.amount!! },
                income = inMonth.filter { it.isCountedIncome }.sumOf { it.amount!! },
                isFuture = start > now
            )
        }
        val active = months.filter { !it.isFuture && (it.spent > 0 || it.income > 0) }
        return YearStats(
            year = year,
            months = months,
            spent = months.sumOf { it.spent },
            income = months.sumOf { it.income },
            averageMonthSpent = active.takeIf { it.isNotEmpty() }?.let { m -> m.sumOf { it.spent } / m.size },
            priciestMonth = months.filter { it.spent > 0 }.maxByOrNull { it.spent }
        )
    }

    fun overall(txs: List<Transaction>, now: Long, zone: TimeZone = TimeZone.getDefault()): OverallStats? {
        val counted = txs.filter { it.isCounted }
        if (counted.isEmpty()) return null

        val spendings = counted.filter { it.isCountedSpending }
        val firstDay = dayStart(counted.minOf { it.timestamp }, zone)
        val today = dayStart(now, zone)

        // Walk the calendar day by day: DST-free arithmetic on millis would be
        // fine in Almaty, but not in every zone a phone can be set to.
        val dayStarts = generateSequence(firstDay) { addDays(it, 1, zone) }
            .takeWhile { it <= today }
            .toList()
        val spentByDay = spendings.groupBy { dayStart(it.timestamp, zone) }
            .mapValues { (_, list) -> list.sumOf { it.amount!! } }

        val totalSpent = spendings.sumOf { it.amount!! }
        val perDay = totalSpent / dayStarts.size

        val calendar = Calendar.getInstance(zone)
        fun weekdayOf(millis: Long): Int =
            // Calendar.MONDAY == 2 … SUNDAY == 1 → 0..6 with Monday first.
            (calendar.apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val weekdayCounts = IntArray(7)
        val weekdaySums = DoubleArray(7)
        dayStarts.forEach { day ->
            val index = weekdayOf(day)
            weekdayCounts[index]++
            weekdaySums[index] += spentByDay[day] ?: 0.0
        }

        val thisWeek = weekStart(now, zone)
        val lastWeeks = (WEEKS - 1 downTo 0).map { back ->
            val start = addDays(thisWeek, -7 * back, zone)
            val end = addDays(start, 7, zone)
            PeriodTotal(start, spendings.filter { it.timestamp in start until end }.sumOf { it.amount!! })
        }

        return OverallStats(
            firstDay = firstDay,
            days = dayStarts.size,
            perDay = perDay,
            perWeek = perDay * 7,
            perMonth = perDay * DAYS_PER_MONTH,
            weekdayAverages = (0 until 7).map { i ->
                if (weekdayCounts[i] == 0) 0.0 else weekdaySums[i] / weekdayCounts[i]
            },
            lastWeeks = lastWeeks,
            priciestDay = spentByDay.maxByOrNull { it.value }?.let { PeriodTotal(it.key, it.value) },
            daysWithoutSpending = dayStarts.count { (spentByDay[it] ?: 0.0) == 0.0 },
            totalSpent = totalSpent,
            totalIncome = counted.filter { it.isCountedIncome }.sumOf { it.amount!! }
        )
    }

    private val Transaction.isCounted: Boolean
        get() = isCountedSpending || isCountedIncome

    private fun monthStart(year: Int, month: Int, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month, 1, 0, 0, 0)
        }.timeInMillis

    private fun dayStart(millis: Long, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun weekStart(millis: Long, zone: TimeZone): Long {
        val day = dayStart(millis, zone)
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = day }
        val sinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        return addDays(day, -sinceMonday, zone)
    }

    private fun addDays(millis: Long, days: Int, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = millis
            add(Calendar.DAY_OF_YEAR, days)
        }.timeInMillis
}
