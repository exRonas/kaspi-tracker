package com.kleinapps.kaspitracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Auto-categorization, checked against the wording that actually shows up in the
 * user's history (notifications, receipts and manual entries alike).
 */
class CategoryClassifierTest {

    private fun classify(
        merchant: String?,
        rawText: String = "",
        type: TxType = TxType.PURCHASE,
        learned: List<CategoryRule> = emptyList()
    ) = CategoryClassifier.classify(merchant, rawText, type, learned)

    // --- keyword rules -----------------------------------------------------

    @Test
    fun `known grocery shops are recognized in any case`() {
        assertEquals(Category.GROCERIES, classify("SMALL"))
        assertEquals(Category.GROCERIES, classify("Small"))
        assertEquals(Category.GROCERIES, classify("Inmart"))
        assertEquals(Category.GROCERIES, classify("Magnum"))
    }

    @Test
    fun `merchant is recognized from the receipt body when the field is empty`() {
        val receipt = """
            Оплата совершена
            922 ₸
            Inmart
            ТОО "НИЗКОЦЕН"
        """.trimIndent()
        assertEquals(Category.GROCERIES, classify(merchant = null, rawText = receipt))
    }

    @Test
    fun `public transport payments are transport, not utilities`() {
        // Regression: "оси" matched inside "гОСномер" and sent every bus ticket
        // to Utilities.
        val receipt = """
            Общественный транспорт
            Билет Avtobys. Оплата проезда
            904AI14
            Платеж успешно совершен
            100,00 ₸
            Госномер транспорта 904AI14
        """.trimIndent()
        assertEquals(Category.TRANSPORT, classify("Общественный транспорт", receipt))
        assertEquals(Category.TRANSPORT, classify(null, "Билет Avtobys. Оплата проезда"))
    }

    @Test
    fun `train tickets land in transport`() {
        assertEquals(Category.TRANSPORT, classify(null, "Оплата проезда, поезд Павлодар - Астана"))
        assertEquals(Category.TRANSPORT, classify("Kaspi Travel", "Ваш билет оплачен"))
    }

    @Test
    fun `short keywords only match at a word start`() {
        // "оси"/"кск"/"газ" must not fire inside a longer word.
        assertNull(classify(null, "Госномер транспорта 904AI14"))
        assertNull(classify(null, "Газета Литер"))
        assertEquals(Category.UTILITIES, classify(null, "Газ, оплата за июль"))
    }

    @Test
    fun `fitness is health`() {
        assertEquals(Category.HEALTH, classify("ИП Старина", "Фитнес-клубы"))
    }

    @Test
    fun `unknown merchant stays unclassified so the user is asked`() {
        assertNull(classify("Lavka Pulman"))
        assertNull(classify("ИП АБДУКАРИМОВА ДАНА"))
    }

    // --- transfers ---------------------------------------------------------

    @Test
    fun `incoming transfer is income`() {
        assertEquals(
            Category.INCOME,
            classify(null, "Пополнение: 204 000 ₸", TxType.TRANSFER_IN)
        )
    }

    @Test
    fun `outgoing transfer needs a human the first time`() {
        assertNull(classify("Умар", "Перевод клиенту Kaspi", TxType.TRANSFER_OUT))
    }

    // --- learned rules -----------------------------------------------------

    private fun rule(merchant: String, category: Category) =
        CategoryRule(MerchantKey.of(merchant)!!, category)

    @Test
    fun `a category picked once is reused for the same merchant`() {
        val learned = listOf(rule("Inmart", Category.OTHER))
        assertEquals(Category.OTHER, classify("Inmart", learned = learned))
        assertEquals(Category.OTHER, classify("inmart", learned = learned))
    }

    @Test
    fun `learned rule beats the keyword list`() {
        // "SMALL" is a grocery by keyword; if the user insists it is something
        // else, their choice wins.
        val learned = listOf(rule("SMALL", Category.CAFE))
        assertEquals(Category.CAFE, classify("SMALL", learned = learned))
    }

    @Test
    fun `learned rule also covers outgoing transfers`() {
        val learned = listOf(rule("Умар", Category.GROCERIES))
        assertEquals(
            Category.GROCERIES,
            classify("Умар", "Перевод клиенту Kaspi", TxType.TRANSFER_OUT, learned)
        )
    }

    @Test
    fun `learned rule is found in the raw text when the merchant was not parsed`() {
        val learned = listOf(rule("Lavka Pulman", Category.CAFE))
        assertEquals(
            Category.CAFE,
            classify(null, "Оплата совершена 1 200 ₸ Lavka Pulman", learned = learned)
        )
    }

    @Test
    fun `learned rule does not fire on a partial word`() {
        val learned = listOf(rule("Умар", Category.GROCERIES))
        assertNull(classify("Умаров Дастан", "Перевод", TxType.TRANSFER_OUT, learned))
    }

    @Test
    fun `the more specific learned rule wins`() {
        val learned = listOf(
            rule("Kaspi", Category.SHOPPING),
            rule("Kaspi Travel", Category.TRANSPORT)
        )
        assertEquals(Category.TRANSPORT, classify(null, "Оплата Kaspi Travel", learned = learned))
    }

    // --- manual entries ----------------------------------------------------

    @Test
    fun `manual entry is categorized from the merchant the user typed`() {
        val raw = "Добавлено вручную"
        assertEquals(Category.GROCERIES, classify("Small", raw))
        assertEquals(Category.SHOPPING, classify("Technodom", raw))
        assertNull(classify("Блины", raw))
    }

    @Test
    fun `manual entry with no merchant is left for review`() {
        assertNull(classify(null, "Добавлено вручную"))
    }
}
