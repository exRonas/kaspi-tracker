package com.kleinapps.kaspitracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MonthForecastTest {

    private fun dayInJuly(day: Int): Long =
        Calendar.getInstance().apply {
            set(2026, Calendar.JULY, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `spending is projected at the current daily rate`() {
        // 100 000 over 10 days of a 31-day month.
        val forecast = forecastMonth(100_000.0, dayInJuly(10))
        assertEquals(10_000.0, forecast.perDay, 0.001)
        assertEquals(310_000.0, forecast.projected, 0.001)
        assertEquals(21, forecast.daysLeft)
    }

    @Test
    fun `the first days of a month are not a trend`() {
        assertFalse(forecastMonth(50_000.0, dayInJuly(1)).reliable)
        assertFalse(forecastMonth(50_000.0, dayInJuly(2)).reliable)
        assertTrue(forecastMonth(50_000.0, dayInJuly(3)).reliable)
    }

    @Test
    fun `on the last day the projection is just the total`() {
        val forecast = forecastMonth(255_000.0, dayInJuly(31))
        assertEquals(255_000.0, forecast.projected, 0.001)
        assertEquals(0, forecast.daysLeft)
    }

    @Test
    fun `month length is taken from the calendar`() {
        val february = Calendar.getInstance().apply {
            set(2026, Calendar.FEBRUARY, 14, 12, 0, 0)
        }.timeInMillis
        assertEquals(28, forecastMonth(1000.0, february).daysInMonth)
    }

    @Test
    fun `overshoot only fires when the limit will actually be passed`() {
        val forecast = forecastMonth(100_000.0, dayInJuly(10)) // projects 310 000
        assertEquals(60_000.0, forecast.overshoot(250_000.0)!!, 0.001)
        assertNull(forecast.overshoot(400_000.0))
        assertNull(forecast.overshoot(null))
        assertNull(forecast.overshoot(0.0))
    }

    @Test
    fun `no spending yet means no projection`() {
        val forecast = forecastMonth(0.0, dayInJuly(10))
        assertEquals(0.0, forecast.projected, 0.001)
    }
}
