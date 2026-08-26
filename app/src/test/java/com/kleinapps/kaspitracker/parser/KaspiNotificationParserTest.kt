package com.kleinapps.kaspitracker.parser

import com.kleinapps.kaspitracker.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KaspiNotificationParserTest {

    @Test
    fun `parses a purchase push`() {
        val parsed = KaspiNotificationParser.parse(
            title = "Kaspi Gold",
            text = "Покупка: 890 ₸\nYANDEX.GO\nДоступно: 102 ₸"
        )
        assertEquals(890.0, parsed.amount!!, 0.001)
        assertEquals(TxType.PURCHASE, parsed.type)
    }

    @Test
    fun `parses a top-up push with non-breaking spaces`() {
        val parsed = KaspiNotificationParser.parse(
            title = "Kaspi Gold",
            text = "Пополнение: 204 000 ₸\nС карты другого банка"
        )
        assertEquals(204_000.0, parsed.amount!!, 0.001)
        assertEquals(TxType.TRANSFER_IN, parsed.type)
    }

    @Test
    fun `amount formats`() {
        assertEquals(12_500.0, KaspiNotificationParser.extractAmount("12 500 ₸")!!, 0.001)
        assertEquals(12_500.75, KaspiNotificationParser.extractAmount("12 500,75 тг")!!, 0.001)
        assertEquals(1_234_567.0, KaspiNotificationParser.extractAmount("1.234.567 KZT")!!, 0.001)
        assertEquals(990.0, KaspiNotificationParser.extractAmount("990 тенге")!!, 0.001)
        assertNull(KaspiNotificationParser.extractAmount("Доступно скоро"))
    }

    @Test
    fun `informational push without an amount stays unparsed but is not lost`() {
        val parsed = KaspiNotificationParser.parse("Платежи", "Билет Avtobys. Оплата проезда")
        assertNull(parsed.amount)
        assertEquals(TxType.PURCHASE, parsed.type)
    }

    // --- counterparty on the line below the amount -------------------------

    @Test
    fun `merchant is the line under the amount`() {
        val parsed = KaspiNotificationParser.parse(
            title = "Kaspi Gold",
            text = "Покупка: 1 580 ₸\nGOOGLE *YouTubePremium\nДоступно: 16 643 ₸"
        )
        assertEquals("GOOGLE *YouTubePremium", parsed.merchant)
        assertEquals(1580.0, parsed.amount!!, 0.001)
    }

    @Test
    fun `the balance line is never taken as a merchant`() {
        val parsed = KaspiNotificationParser.parse("Kaspi Gold", "Покупка: 890 ₸\nДоступно: 102 ₸")
        assertNull(parsed.merchant)
    }

    @Test
    fun `sender of an incoming transfer is the counterparty`() {
        val parsed = KaspiNotificationParser.parse("Kaspi Gold", "Пополнение: 66 000 ₸\nАсем С.")
        assertEquals("Асем С.", parsed.merchant)
        assertEquals(TxType.TRANSFER_IN, parsed.type)
    }

    @Test
    fun `a comment after the name is cut off so the person stays one merchant`() {
        val withNote = KaspiNotificationParser.parse(
            "Kaspi Gold", "Пополнение: 2 000 ₸\nТатьяна Ц.: за Алису"
        )
        val plain = KaspiNotificationParser.parse("Kaspi Gold", "Пополнение: 500 ₸\nТатьяна Ц.")
        assertEquals("Татьяна Ц.", withNote.merchant)
        assertEquals(plain.merchant, withNote.merchant)
    }

    @Test
    fun `card name from the title becomes the account`() {
        assertEquals(
            "Kaspi Gold",
            KaspiNotificationParser.parse("Kaspi Gold", "Покупка: 890 ₸\nSMALL").account
        )
    }

    @Test
    fun `marketing pushes do not invent an account`() {
        assertNull(KaspiNotificationParser.parse("Магазин на Kaspi.kz", "Скидки!").account)
        assertNull(KaspiNotificationParser.parse("Kaspi.kz", "Скидки!").account)
        assertNull(KaspiNotificationParser.parse("Kaspi Travel", "Ваш билет оплачен").account)
    }

    @Test
    fun `merchant falls back to the in-text form when there is no line below`() {
        assertEquals(
            "Magnum",
            KaspiNotificationParser.parse(null, "Покупка в Magnum на 3 000 ₸").merchant
        )
    }

    // --- duplicate fingerprint ---------------------------------------------

    @Test
    fun `the group summary and the notification itself share one key`() {
        // Kaspi posts both within milliseconds; only the title differs.
        val summary = KaspiNotificationParser.dedupKey("Билет Avtobys. Оплата проезда", null)
        val child = KaspiNotificationParser.dedupKey("Билет Avtobys.  Оплата  проезда", null)
        assertEquals(summary, child)
    }

    @Test
    fun `different bodies get different keys`() {
        assertNotEquals(
            KaspiNotificationParser.dedupKey("Покупка: 890 ₸ YANDEX.GO", 890.0),
            KaspiNotificationParser.dedupKey("Покупка: 890 ₸ SMALL", 890.0)
        )
    }

    @Test
    fun `same text with a different amount is a different operation`() {
        assertNotEquals(
            KaspiNotificationParser.dedupKey("Покупка в SMALL", 500.0),
            KaspiNotificationParser.dedupKey("Покупка в SMALL", 700.0)
        )
    }

    @Test
    fun `empty body has no key`() {
        assertNull(KaspiNotificationParser.dedupKey(null, 100.0))
        assertNull(KaspiNotificationParser.dedupKey("   ", 100.0))
    }
}
