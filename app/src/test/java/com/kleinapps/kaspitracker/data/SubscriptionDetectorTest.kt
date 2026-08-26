package com.kleinapps.kaspitracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionDetectorTest {

    private val day = 24L * 60 * 60 * 1000
    private val start = 1_700_000_000_000L

    /** Fixtures are dated from [start]; "now" sits just after the last of them. */
    private fun detect(payments: List<Transaction>, atDay: Int = 400) =
        SubscriptionDetector.detect(payments, now = start + atDay * day)

    private fun payment(
        merchant: String,
        amount: Double,
        daysFromStart: Int,
        rawText: String = merchant,
        category: Category? = null
    ) = Transaction(
        timestamp = start + daysFromStart * day,
        title = null,
        rawText = rawText,
        amount = amount,
        type = TxType.PURCHASE,
        merchant = merchant,
        category = category
    )

    @Test
    fun `two charges a month apart are a monthly subscription`() {
        val found = detect(
            listOf(
                payment("YANDEX.PLUS", 1999.0, 0),
                payment("YANDEX.PLUS", 1999.0, 30)
            )
        )
        val subscription = found.single()
        assertEquals(Cadence.MONTHLY, subscription.cadence)
        assertTrue(subscription.confirmed)
        assertEquals(2, subscription.payments)
        assertEquals(1999.0, subscription.monthlyCost, 0.001)
    }

    @Test
    fun `a price rise does not break the subscription`() {
        val found = detect(
            listOf(
                payment("GOOGLE *YouTubePremium", 1580.0, 0),
                payment("GOOGLE *YouTubePremium", 1780.0, 31)
            )
        )
        // The newest charge is the one quoted.
        assertEquals(1780.0, found.single().amount, 0.001)
    }

    @Test
    fun `weekly and yearly cadences are recognized`() {
        // Weekly needs three charges in a row before it is believed.
        val weekly = detect(
            listOf(
                payment("Спортзал", 3000.0, 0),
                payment("Спортзал", 3000.0, 7),
                payment("Спортзал", 3000.0, 14)
            )
        ).single()
        assertEquals(Cadence.WEEKLY, weekly.cadence)

        val yearly = detect(
            listOf(
                payment("Kaspi", 1995.0, 0, rawText = "За годовое обслуживание"),
                payment("Kaspi", 1995.0, 365, rawText = "За годовое обслуживание")
            )
        ).single()
        assertEquals(Cadence.YEARLY, yearly.cadence)
    }

    @Test
    fun `two shopping trips a week apart are not a weekly subscription`() {
        // Regression: "Умар" and "ИП САДЫГОВ" were reported as weekly
        // subscriptions purely because two grocery runs landed 7 days apart.
        val found = detect(
            listOf(payment("ИП САДЫГОВ К.Н.", 600.0, 0), payment("ИП САДЫГОВ К.Н.", 600.0, 7))
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun `a weekly charge that drifts in price is not a subscription`() {
        val found = detect(
            listOf(
                payment("Крендель", 400.0, 0),
                payment("Крендель", 520.0, 7),
                payment("Крендель", 360.0, 14)
            )
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun `everyday shopping is not a subscription`() {
        val found = detect(
            listOf(
                payment("SMALL", 533.0, 0),
                payment("SMALL", 1240.0, 2),
                payment("SMALL", 890.0, 5)
            )
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun `a known service is reported after one charge but not confirmed`() {
        val subscription = detect(
            listOf(payment("GOOGLE *YouTubePremium", 1580.0, 0))
        ).single()
        assertFalse(subscription.confirmed)
        assertEquals(null, subscription.cadence)
        assertEquals(null, subscription.nextExpected)
    }

    @Test
    fun `an unknown merchant paid once is ignored`() {
        assertTrue(detect(listOf(payment("Дом Хлеба", 1170.0, 0))).isEmpty())
    }

    @Test
    fun `next charge is one period after the last one`() {
        val subscription = detect(
            listOf(payment("Netflix", 4990.0, 0), payment("Netflix", 4990.0, 30)),
            atDay = 45
        ).single()
        assertEquals(start + 60 * day, subscription.nextExpected)
    }

    @Test
    fun `a charge date already gone by is not offered as a forecast`() {
        val subscription = detect(
            listOf(payment("Netflix", 4990.0, 0), payment("Netflix", 4990.0, 30)),
            atDay = 200
        ).single()
        assertEquals(null, subscription.nextExpected)
    }

    @Test
    fun `the same shop written differently is one subscription`() {
        val found = detect(
            listOf(payment("Netflix", 4990.0, 0), payment("NETFLIX", 4990.0, 30))
        )
        assertEquals(1, found.size)
    }

    @Test
    fun `most expensive per month comes first`() {
        val found = detect(
            listOf(
                payment("Netflix", 4990.0, 0), payment("Netflix", 4990.0, 30),
                payment("Спортзал", 3000.0, 0), payment("Спортзал", 3000.0, 7),
                payment("Спортзал", 3000.0, 14)
            )
        )
        // Weekly 3000 (~13 000/month) outweighs monthly 4990.
        assertEquals("Спортзал", found.first().merchant)
    }
}
