package com.kleinapps.kaspitracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {

    private fun tx(uuid: String, timestamp: Long, shared: Boolean = false) = Transaction(
        timestamp = timestamp, title = null, rawText = "", amount = 100.0,
        type = TxType.PURCHASE, merchant = null, uuid = uuid, shared = shared
    )

    @Test
    fun `together shows only shared rows of both people newest first`() {
        val mine = listOf(tx("a", 3, shared = true), tx("b", 5))
        val partner = listOf(tx("c", 4, shared = true), tx("d", 1))

        val entries = ledgerFor(ViewScope.TOGETHER, mine, partner)

        assertEquals(listOf("c", "a"), entries.map { it.tx.uuid })
        assertEquals(listOf(Owner.PARTNER, Owner.ME), entries.map { it.owner })
    }

    @Test
    fun `partner scope marks every entry as partner`() {
        val entries = ledgerFor(ViewScope.PARTNER, listOf(tx("a", 1)), listOf(tx("c", 2), tx("d", 3)))

        assertEquals(listOf("d", "c"), entries.map { it.tx.uuid })
        assertTrue(entries.all { it.owner == Owner.PARTNER })
    }

    @Test
    fun `my scope keeps own and shared rows alike`() {
        val entries = ledgerFor(ViewScope.ME, listOf(tx("a", 1, shared = true), tx("b", 2)), emptyList())
        assertEquals(listOf("b", "a"), entries.map { it.tx.uuid })
    }

    @Test
    fun `excluded rows are not counted as spending or income`() {
        val excluded = tx("a", 1).copy(excludedFromStats = true)
        assertFalse(excluded.isCountedSpending)
        assertFalse(excluded.copy(type = TxType.TRANSFER_IN).isCountedIncome)
        assertTrue(tx("b", 1).isCountedSpending)
        assertTrue(tx("c", 1).copy(type = TxType.TRANSFER_IN).isCountedIncome)
    }

    @Test
    fun `latest balance per account comes from the newest reading`() {
        val rows = listOf(
            tx("a", 1).copy(account = "Kaspi Gold", balance = 100.0),
            tx("b", 2).copy(account = "Kaspi Gold", balance = 50.0),
            tx("c", 1).copy(account = "Kaspi Депозит", balance = 1_000.0),
            tx("d", 3).copy(account = null, balance = 7.0)
        )
        val balances = latestBalances(rows).associate { it.account to it.balance }

        assertEquals(mapOf("Kaspi Gold" to 50.0, "Kaspi Депозит" to 1_000.0), balances)
    }

    @Test
    fun `balance history is oldest first per card`() {
        val rows = listOf(
            tx("a", 2).copy(account = "Kaspi Gold", balance = 50.0),
            tx("b", 1).copy(account = "Kaspi Gold", balance = 100.0)
        )
        assertEquals(listOf(100.0, 50.0), balanceHistory(rows)["Kaspi Gold"])
    }
}
