package com.kleinapps.kaspitracker.util

import java.util.Calendar

/**
 * Where this month's spending is heading if nothing changes.
 *
 * [reliable] is false for the first couple of days, when one big purchase makes
 * the projection meaningless — the number is still computed, the UI just says
 * so instead of pretending it is a forecast.
 */
data class MonthForecast(
    val spent: Double,
    val projected: Double,
    val perDay: Double,
    val dayOfMonth: Int,
    val daysInMonth: Int
) {
    val daysLeft: Int get() = daysInMonth - dayOfMonth
    val reliable: Boolean get() = dayOfMonth >= MIN_DAYS_FOR_TREND

    /** Projected overshoot against a limit, or null when it fits. */
    fun overshoot(limit: Double?): Double? =
        limit?.takeIf { it > 0 && projected > it }?.let { projected - it }

    companion object {
        const val MIN_DAYS_FOR_TREND = 3
    }
}

/**
 * Projects [spent] so far in the current month onto the whole month, at the
 * average daily rate. Days are counted whole: on the 10th, ten days have been
 * paid for, however far into the day it is.
 */
fun forecastMonth(spent: Double, now: Long = System.currentTimeMillis()): MonthForecast {
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val perDay = if (dayOfMonth > 0) spent / dayOfMonth else 0.0

    return MonthForecast(
        spent = spent,
        projected = perDay * daysInMonth,
        perDay = perDay,
        dayOfMonth = dayOfMonth,
        daysInMonth = daysInMonth
    )
}
