package com.kleinapps.kaspitracker.receipt

import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.CategoryClassifier
import com.kleinapps.kaspitracker.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Receipt texts below are copied verbatim from receipts the user actually
 * imported (PDF text layer and OCR output alike).
 */
class KaspiReceiptParserTest {

    @Test
    fun `shop receipt gives amount, merchant and account`() {
        val parsed = KaspiReceiptParser.parse(
            """
            Оплата совершена
            533 ₸
            SMALL
            ТОО "Скиф трейд"
            № чека QR16786649260
            ИИН/БИН продавца 020340002288
            ФИО покупателя Никита З.
            Дата и время
            по Астане
            30.07.2026 22:50
            Оплачено с Kaspi Gold
            Адрес г. Павлодар, Кутузова, 41
            Чек не является фискальным
            """.trimIndent()
        )
        assertEquals(533.0, parsed.amount!!, 0.001)
        assertEquals("SMALL", parsed.counterparty)
        assertEquals(TxType.PURCHASE, parsed.type)
        assertNotNull(parsed.timestamp)
        assertEquals(Category.GROCERIES, CategoryClassifier.classify(parsed.counterparty, parsed.rawText, parsed.type))
    }

    @Test
    fun `transport receipt uses the service name, not the per-ride receipt number`() {
        val parsed = KaspiReceiptParser.parse(
            """
            Общественный транспорт
            Билет Avtobys. Оплата
            проезда
            904AI14
            Платеж успешно совершен
            100,00 ₸
            № квитанции QR16793561441
            Идентификатор 231980628962
            Госномер транспорта 904AI14
            Дата и время по Астане 31.07.2026 13:37:30
            ФИО плательщика Зимонин Н. А.
            Оплачено с Kaspi Gold
            """.trimIndent()
        )
        assertEquals(100.0, parsed.amount!!, 0.001)
        assertEquals("Общественный транспорт", parsed.counterparty)
        // Regression: this used to be "№ квитанции QR16793561441" — a brand-new
        // merchant on every ride, so the category had to be picked by hand each time.
        assertTrue(parsed.counterparty!!.none(Char::isDigit))
        assertEquals(
            Category.TRANSPORT,
            CategoryClassifier.classify(parsed.counterparty, parsed.rawText, parsed.type)
        )
    }

    @Test
    fun `two rides of the same route produce the same merchant`() {
        fun ride(plate: String, receipt: String) = KaspiReceiptParser.parse(
            """
            Общественный транспорт
            Билет Avtobys. Оплата
            проезда
            $plate
            Платеж успешно совершен
            100,00 ₸
            № квитанции $receipt
            Госномер транспорта $plate
            Дата и время по Астане 31.07.2026 13:37:30
            """.trimIndent()
        ).counterparty

        assertEquals(ride("904AI14", "QR16793561441"), ride("846AI14", "QR16790044424"))
    }

    @Test
    fun `fiscal receipt still reads the item line as merchant`() {
        val parsed = KaspiReceiptParser.parse(
            """
            Фискальный чек
            Оплата совершена
            1 140 ₸
            продукты
            ИП БАЛГОЖИНА АЙНАШ БОРЫБАЕВНА
            Продажа
            Продукты питания
            № чека QR16790207628
            Дата и время
            по Астане
            31.07.2026 10:43
            Оплачено с Kaspi Gold
            """.trimIndent()
        )
        assertEquals(1140.0, parsed.amount!!, 0.001)
        assertEquals("продукты", parsed.counterparty)
    }

    @Test
    fun `transfer receipt read by OCR`() {
        val parsed = KaspiReceiptParser.parse(
            """
            (®
            Перевод успешно совершен
            703 т
            Umar А.
            Тип перевода Перевод
            клиенту Kaspi
            № квитанции 870914906035266706
            Дата и время 31.07.2026 11:24
            по Астане
            Комиссия OT
            Отправитель Зимонин Н.А.
            Откуда Kaspi Gold
            """.trimIndent()
        )
        assertEquals(703.0, parsed.amount!!, 0.001)
        assertEquals("Umar А.", parsed.counterparty)
        assertEquals(TxType.TRANSFER_OUT, parsed.type)
        assertEquals("Kaspi Gold", parsed.account)
    }
}
