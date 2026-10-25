package com.kleinapps.kaspitracker.data

import com.kleinapps.kaspitracker.parser.KaspiNotificationParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end behaviour of adding operations — the notification path, the manual
 * path and the receipt path — over in-memory DAOs.
 */
class TransactionRepositoryTest {

    private lateinit var dao: FakeTransactionDao
    private lateinit var ruleDao: FakeCategoryRuleDao
    private lateinit var repo: TransactionRepository

    @Before
    fun setUp() {
        dao = FakeTransactionDao()
        ruleDao = FakeCategoryRuleDao()
        repo = fakeRepository(dao, ruleDao)
    }

    private fun notification(
        body: String,
        title: String? = null,
        timestamp: Long = 1_000_000L
    ): Transaction {
        val parsed = KaspiNotificationParser.parse(title, body)
        return Transaction(
            timestamp = timestamp,
            title = title,
            rawText = listOfNotNull(title, body).joinToString(" — "),
            amount = parsed.amount,
            type = parsed.type,
            merchant = parsed.merchant,
            account = parsed.account,
            dedupKey = KaspiNotificationParser.dedupKey(body, parsed.amount)
        )
    }

    private fun manual(
        merchant: String?,
        amount: Double = 500.0,
        type: TxType = TxType.PURCHASE
    ) = Transaction(
        timestamp = 2_000_000L,
        title = null,
        rawText = "Добавлено вручную",
        amount = amount,
        type = type,
        merchant = merchant,
        source = TxSource.MANUAL
    )

    // --- duplicate notifications -------------------------------------------

    @Test
    fun `the second copy of the same push is dropped`() = runTest {
        // Kaspi posts the group summary and the notification itself milliseconds
        // apart: same body, different title.
        val body = "Билет Avtobys. Оплата проезда"
        assertNotNull(repo.insertNotification(notification(body, title = "Платежи", timestamp = 1_000_000L)))
        assertNull(repo.insertNotification(notification(body, title = null, timestamp = 1_000_008L)))
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun `a purchase push is not swallowed by an earlier different one`() = runTest {
        repo.insertNotification(notification("Покупка: 890 ₸ YANDEX.GO"))
        repo.insertNotification(notification("Покупка: 500 ₸ SMALL"))
        assertEquals(2, dao.rows.value.size)
    }

    @Test
    fun `the same purchase repeated later is kept`() = runTest {
        val body = "Покупка: 100 ₸ SMALL"
        repo.insertNotification(notification(body, timestamp = 1_000_000L))
        repo.insertNotification(
            notification(body, timestamp = 1_000_000L + TransactionRepository.NOTIFICATION_DEDUP_WINDOW_MS + 1)
        )
        assertEquals(2, dao.rows.value.size)
    }

    @Test
    fun `the dropped copy still hands over the card it carried`() = runTest {
        // The untitled copy usually lands first, so the account name would be
        // lost unless the duplicate fills it in.
        val body = "Покупка: 890 ₸\nYANDEX.GO\nДоступно: 102 ₸"
        repo.insertNotification(notification(body, title = null, timestamp = 1_000_000L))
        repo.insertNotification(notification(body, title = "Kaspi Gold", timestamp = 1_000_400L))

        val row = dao.rows.value.single()
        assertEquals("Kaspi Gold", row.account)
        assertEquals("Kaspi Gold", row.title)
    }

    @Test
    fun `manual entries are never deduplicated against each other`() = runTest {
        repo.insert(manual("SMALL"))
        repo.insert(manual("SMALL"))
        assertEquals(2, dao.rows.value.size)
    }

    // --- automatic vs manual categorization --------------------------------

    @Test
    fun `manual entry gets a category from the merchant`() = runTest {
        repo.insert(manual("Small"))
        assertEquals(Category.GROCERIES, dao.rows.value.single().category)
    }

    @Test
    fun `notification for a known shop is categorized on the way in`() = runTest {
        repo.insertNotification(notification("Покупка: 890 ₸ YANDEX.GO"))
        assertEquals(Category.TRANSPORT, dao.rows.value.single().category)
    }

    @Test
    fun `unknown merchant is left for the review queue`() = runTest {
        repo.insert(manual("Lavka Pulman"))
        assertNull(dao.rows.value.single().category)
    }

    @Test
    fun `an explicit category chosen by the user is not overwritten`() = runTest {
        repo.insert(manual("Small").copy(category = Category.CAFE))
        assertEquals(Category.CAFE, dao.rows.value.single().category)
    }

    // --- learning from corrections -----------------------------------------

    @Test
    fun `answering once teaches the merchant for good`() = runTest {
        repo.insert(manual("Lavka Pulman"))
        repo.assignCategory(dao.rows.value.single(), Category.CAFE)

        repo.insert(manual("lavka pulman", amount = 700.0))
        assertEquals(Category.CAFE, dao.rows.value.last().category)
    }

    @Test
    fun `answering once also clears the backlog for that merchant`() = runTest {
        repeat(3) { repo.insert(manual("Lavka Pulman").copy(timestamp = 2_000_000L + it)) }
        assertEquals(3, dao.rows.value.count { it.category == null })

        repo.assignCategory(dao.rows.value.first(), Category.OTHER)
        assertEquals(0, dao.rows.value.count { it.category == null })
        assertEquals(3, dao.rows.value.count { it.category == Category.OTHER })
    }

    @Test
    fun `editing a category in history teaches the rule too`() = runTest {
        repo.insert(manual("Greenwich"))
        val row = dao.rows.value.single()
        repo.update(row.copy(category = Category.CAFE))

        repo.insert(manual("Greenwich", amount = 300.0))
        assertEquals(Category.CAFE, dao.rows.value.last().category)
    }

    @Test
    fun `editing something other than the category teaches nothing`() = runTest {
        repo.insert(manual("Small"))
        val row = dao.rows.value.single()
        repo.update(row.copy(amount = 999.0))
        assertEquals(emptyList<CategoryRule>(), ruleDao.rules.value)
    }

    @Test
    fun `a learned rule covers outgoing transfers to the same person`() = runTest {
        repo.insert(manual("Умар", type = TxType.TRANSFER_OUT))
        assertNull(dao.rows.value.single().category)

        repo.assignCategory(dao.rows.value.single(), Category.GROCERIES)
        repo.insert(manual("Умар", amount = 703.0, type = TxType.TRANSFER_OUT))
        assertEquals(Category.GROCERIES, dao.rows.value.last().category)
    }

    @Test
    fun `receipt numbers never become a rule`() = runTest {
        repo.insert(manual("№ квитанции QR16793561441"))
        repo.assignCategory(dao.rows.value.single(), Category.TRANSPORT)
        assertEquals(emptyList<CategoryRule>(), ruleDao.rules.value)
    }

    // --- backfill and seeding ----------------------------------------------

    @Test
    fun `backfill applies newly learned rules to the queue`() = runTest {
        repo.insert(manual("Lavka Pulman"))
        repo.learn("Lavka Pulman", Category.CAFE)
        // learn() already fixes the queue; backfill must stay idempotent.
        repo.backfillCategories()
        assertEquals(Category.CAFE, dao.rows.value.single().category)
    }

    @Test
    fun `old pushes get their merchant read out of the stored text`() = runTest {
        dao.insert(
            Transaction(
                timestamp = 1L,
                title = "Kaspi Gold",
                rawText = "Kaspi Gold — Покупка: 2 431 ₸\nInmart\nДоступно: 760 ₸",
                amount = 2431.0,
                type = TxType.PURCHASE,
                merchant = null
            )
        )
        repo.backfillNotificationMerchants()

        val row = dao.rows.value.single()
        assertEquals("Inmart", row.merchant)
        assertEquals("Kaspi Gold", row.account)
        // The "Доступно" line is read back out of the stored text too.
        assertEquals(760.0, row.balance!!, 0.001)
    }

    @Test
    fun `rules are seeded from what the user already categorized`() = runTest {
        dao.insert(manual("Inmart").copy(category = Category.GROCERIES, timestamp = 1L))
        dao.insert(manual("Inmart").copy(category = Category.OTHER, timestamp = 2L))
        dao.insert(manual("№ квитанции QR16793561441").copy(category = Category.TRANSPORT, timestamp = 3L))

        repo.seedRulesFromHistory()

        // Most recent choice per merchant wins; receipt numbers are skipped.
        assertEquals(1, ruleDao.rules.value.size)
        assertEquals(Category.OTHER, ruleDao.rules.value.single().category)
    }

    @Test
    fun `seeding does not overwrite rules that already exist`() = runTest {
        repo.learn("Inmart", Category.CAFE)
        dao.insert(manual("Inmart").copy(category = Category.GROCERIES, timestamp = 1L))

        repo.seedRulesFromHistory()
        assertEquals(Category.CAFE, ruleDao.rules.value.single().category)
    }

    // --- receipt meets notification ----------------------------------------

    private fun receipt(
        amount: Double,
        merchant: String?,
        timestamp: Long,
        rawText: String = "Оплата совершена"
    ) = Transaction(
        timestamp = timestamp,
        title = "Импортированный чек",
        rawText = rawText,
        amount = amount,
        type = TxType.PURCHASE,
        merchant = merchant,
        source = TxSource.RECEIPT,
        account = "Kaspi Gold"
    )

    @Test
    fun `a receipt for a payment already pushed does not double the total`() = runTest {
        repo.insertNotification(
            notification("Покупка: 533 ₸\nSMALL\nДоступно: 102 ₸", timestamp = 1_000_000L)
        )
        // Receipts carry minute-precision timestamps, so they never match to the ms.
        val merged = repo.insertReceipt(
            receipt(533.0, "SMALL", 1_000_000L + 4 * 60_000, rawText = "Оплата совершена 533 ₸ SMALL")
        )

        assertNull(merged)
        val row = dao.rows.value.single()
        assertEquals(TxSource.RECEIPT, row.source)
        assertEquals("Kaspi Gold", row.account)
        assertEquals("Оплата совершена 533 ₸ SMALL", row.rawText)
    }

    @Test
    fun `a receipt with no matching push is stored on its own`() = runTest {
        repo.insertNotification(
            notification("Покупка: 533 ₸\nSMALL\nДоступно: 102 ₸", timestamp = 1_000_000L)
        )
        repo.insertReceipt(receipt(1140.0, "продукты", 1_000_000L + 60_000))
        assertEquals(2, dao.rows.value.size)
    }

    @Test
    fun `a payment of the same size hours later is a separate operation`() = runTest {
        repo.insertNotification(
            notification("Покупка: 533 ₸\nSMALL\nДоступно: 102 ₸", timestamp = 1_000_000L)
        )
        repo.insertReceipt(receipt(533.0, "SMALL", 1_000_000L + 3 * 60 * 60 * 1000))
        assertEquals(2, dao.rows.value.size)
    }

    // --- transfers between own cards ---------------------------------------

    private fun cardTx(
        amount: Double,
        type: TxType,
        account: String,
        timestamp: Long
    ) = Transaction(
        timestamp = timestamp,
        title = account,
        rawText = "push",
        amount = amount,
        type = type,
        merchant = null,
        account = account
    )

    @Test
    fun `moving money to own deposit is not spending and not income`() = runTest {
        val now = System.currentTimeMillis()
        repo.insert(cardTx(50_000.0, TxType.TRANSFER_OUT, "Kaspi Gold", now))
        repo.insert(cardTx(50_000.0, TxType.TRANSFER_IN, "Kaspi Депозит", now + 3_000))

        assertEquals(2, dao.rows.value.size)
        assertTrue(dao.rows.value.all { it.excludedFromStats })
    }

    @Test
    fun `a real purchase keeps counting`() = runTest {
        val now = System.currentTimeMillis()
        repo.insert(cardTx(50_000.0, TxType.TRANSFER_OUT, "Kaspi Gold", now))
        repo.insert(cardTx(1_240.0, TxType.PURCHASE, "Kaspi Gold", now + 3_000))

        val purchase = dao.rows.value.first { it.amount == 1_240.0 }
        assertFalse(purchase.excludedFromStats)
    }

    // --- backup ------------------------------------------------------------

    @Test
    fun `restoring the same backup twice adds nothing the second time`() = runTest {
        repo.insert(manual("Small", amount = 500.0))
        repo.insert(manual("Lavka Pulman", amount = 700.0))
        repo.learn("Lavka Pulman", Category.CAFE)
        val json = repo.exportBackup()

        val fresh = fakeRepository()
        val first = fresh.importBackup(json)
        val second = fresh.importBackup(json)

        assertEquals(2, first.added)
        assertEquals(0, second.added)
        assertEquals(2, second.skipped)
    }

    @Test
    fun `restoring a row whose uuid already exists is skipped`() = runTest {
        val id = repo.insert(manual("Magnum"))
        val stored = dao.getById(id)!!
        val json = Backup.encode(
            Backup.Content(listOf(stored.copy(rawText = "изменён")), emptyList())
        )

        assertEquals(0, repo.importBackup(json).added)
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun `a backup carries the learned rules over`() = runTest {
        repo.learn("Lavka Pulman", Category.CAFE)
        val json = repo.exportBackup()

        val rules = FakeCategoryRuleDao()
        val fresh = fakeRepository(ruleDao = rules)
        fresh.importBackup(json)

        assertEquals(Category.CAFE, rules.rules.value.single().category)
    }

    // --- receipts ----------------------------------------------------------

    @Test
    fun `re-importing the same receipt is detected`() = runTest {
        val receipt = Transaction(
            timestamp = 5_000_000L,
            title = "Импортированный чек",
            rawText = "Оплата совершена 533 ₸ SMALL",
            amount = 533.0,
            type = TxType.PURCHASE,
            merchant = "SMALL",
            source = TxSource.RECEIPT
        )
        repo.insert(receipt)
        assertTrue(repo.isReceiptDuplicate(5_000_000L, 533.0))
        assertFalse(repo.isReceiptDuplicate(5_000_000L, 999.0))
    }
}
