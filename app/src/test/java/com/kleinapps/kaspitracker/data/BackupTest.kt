package com.kleinapps.kaspitracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupTest {

    private val transaction = Transaction(
        id = 42,
        timestamp = 1_785_487_020_000,
        title = "Kaspi Gold",
        rawText = "Kaspi Gold — Покупка: 2 431 ₸\nInmart\nДоступно: 760 ₸",
        amount = 2431.0,
        type = TxType.PURCHASE,
        merchant = "Inmart",
        source = TxSource.NOTIFICATION,
        category = Category.GROCERIES,
        account = "Kaspi Gold",
        dedupKey = "покупка 2 431 inmart доступно 760|2431.0",
        balance = 760.0
    )

    @Test
    fun `a backup survives the round trip`() {
        val content = Backup.Content(
            transactions = listOf(transaction),
            budgets = listOf(Budget(Category.GROCERIES, 60_000.0)),
            rules = listOf(CategoryRule("inmart", Category.GROCERIES, 123L))
        )
        val restored = Backup.decode(Backup.encode(content))

        val tx = restored.transactions.single()
        assertEquals(transaction.timestamp, tx.timestamp)
        assertEquals(transaction.rawText, tx.rawText)
        assertEquals(transaction.amount, tx.amount)
        assertEquals(transaction.type, tx.type)
        assertEquals(transaction.merchant, tx.merchant)
        assertEquals(transaction.source, tx.source)
        assertEquals(transaction.category, tx.category)
        assertEquals(transaction.account, tx.account)
        assertEquals(transaction.balance, tx.balance)
        // Row ids are local to a device and deliberately not carried over.
        assertEquals(0L, tx.id)

        assertEquals(Budget(Category.GROCERIES, 60_000.0), restored.budgets.single())
        assertEquals(CategoryRule("inmart", Category.GROCERIES, 123L), restored.rules.single())
    }

    @Test
    fun `empty optional fields come back as null, not as the text "null"`() {
        val bare = transaction.copy(
            title = null, merchant = null, amount = null,
            category = null, account = null, dedupKey = null, balance = null
        )
        val tx = Backup.decode(
            Backup.encode(Backup.Content(listOf(bare), emptyList(), emptyList()))
        ).transactions.single()

        assertNull(tx.title)
        assertNull(tx.merchant)
        assertNull(tx.amount)
        assertNull(tx.category)
        assertNull(tx.account)
        assertNull(tx.balance)
    }

    @Test
    fun `an unreadable enum falls back instead of losing the row`() {
        val json = """
            {"version":1,"transactions":[
              {"timestamp":1,"rawText":"x","type":"SOMETHING_NEW","source":"FUTURE",
               "category":"NOT_A_CATEGORY"}
            ]}
        """.trimIndent()
        val tx = Backup.decode(json).transactions.single()
        assertEquals(TxType.UNKNOWN, tx.type)
        assertEquals(TxSource.NOTIFICATION, tx.source)
        assertNull(tx.category)
    }

    @Test
    fun `identity ignores the local row id`() {
        assertEquals(
            Backup.identityOf(transaction),
            Backup.identityOf(transaction.copy(id = 999))
        )
    }

    @Test
    fun `identity separates two payments of the same size`() {
        val later = transaction.copy(timestamp = transaction.timestamp + 60_000)
        assert(Backup.identityOf(transaction) != Backup.identityOf(later))
    }
}
