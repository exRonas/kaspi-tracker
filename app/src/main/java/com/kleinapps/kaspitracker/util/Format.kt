package com.kleinapps.kaspitracker.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val moneyFormat = java.text.DecimalFormat("#,##0.##").apply {
    val symbols = decimalFormatSymbols
    symbols.groupingSeparator = ' '
    symbols.decimalSeparator = ','
    decimalFormatSymbols = symbols
}

/** "12 500,75 ₸" */
fun formatMoney(amount: Double?, currency: String = "₸"): String =
    if (amount == null) "—" else "${moneyFormat.format(amount)} $currency"

private val wholeMoneyFormat = java.text.DecimalFormat("#,##0").apply {
    val symbols = decimalFormatSymbols
    symbols.groupingSeparator = ' '
    decimalFormatSymbols = symbols
}

/**
 * "12 501 ₸" — for totals, projections and averages, where tiyn are noise and
 * a trailing ",5" only makes a headline figure look unfinished.
 */
fun formatMoneyWhole(amount: Double?, currency: String = "₸"): String =
    if (amount == null) "—" else "${wholeMoneyFormat.format(amount)} $currency"

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayFormat = SimpleDateFormat("d MMMM", Locale("ru"))
private val dayYearFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
private val weekdayFormat = SimpleDateFormat("EEE", Locale("ru"))

fun formatTime(millis: Long): String = timeFormat.format(millis)

fun formatDayHeader(millis: Long): String {
    val now = Calendar.getInstance()
    val that = Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(Calendar.YEAR) == that.get(Calendar.YEAR)
    return when {
        isSameDay(now, that) -> "Сегодня"
        isYesterday(now, that) -> "Вчера"
        sameYear -> dayFormat.format(millis)
        else -> dayYearFormat.format(millis)
    }
}

fun formatWeekday(millis: Long): String =
    weekdayFormat.format(millis).replaceFirstChar { it.uppercase() }

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private fun isYesterday(now: Calendar, that: Calendar): Boolean {
    val y = now.clone() as Calendar
    y.add(Calendar.DAY_OF_YEAR, -1)
    return isSameDay(y, that)
}

/** Start-of-day epoch millis for the day [daysAgo] days before [now]. */
fun startOfDay(now: Long = System.currentTimeMillis(), daysAgo: Int = 0): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -daysAgo)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}

fun startOfMonth(now: Long = System.currentTimeMillis()): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return c.timeInMillis
}

private val fullDateFormat = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))

/** "пятница, 22 августа" — the header line under the app name. */
fun formatFullDate(millis: Long): String =
    fullDateFormat.format(millis).replaceFirstChar { it.uppercase() }

/** Time-of-day greeting, so the top of the screen is addressed to someone. */
fun greeting(millis: Long = System.currentTimeMillis()): String {
    val hour = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "ДОБРОЕ УТРО"
        in 12..17 -> "ДОБРЫЙ ДЕНЬ"
        in 18..22 -> "ДОБРЫЙ ВЕЧЕР"
        else -> "ДОБРОЙ НОЧИ"
    }
}
