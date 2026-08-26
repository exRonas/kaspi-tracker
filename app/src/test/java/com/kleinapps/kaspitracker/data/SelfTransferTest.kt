package com.kleinapps.kaspitracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfTransferTest {

    private val base = 1_785_000_000_000L
    private val ownAccounts = setOf("Kaspi Gold", "Kaspi Депозит")

    private fun tx(
        id: Long,
        amount: Double,
        type: TxType,
        account: String?,
        offsetMs: Long = 0
    ) = Transaction(
        id = id,
        timestamp = base + offsetMs,
        title = account,
        rawText = "push",
        amount = amount,
        type = type,
        merchant = null,
        account = account
    )

    @Test
    fun `money moved between own cards is matched in both directions`() {
        val out = tx(1, 50_000.0, TxType.TRANSFER_OUT, "Kaspi Gold")
        val income = tx(2, 50_000.0, TxType.TRANSFER_IN, "Kaspi Депозит", offsetMs = 4_000)

        assertEquals(income, SelfTransfer.matchIn(out, listOf(income), ownAccounts))
        assertEquals(out, SelfTransfer.matchIn(income, listOf(out), ownAccounts))
    }

    @Test
    fun `a purchase of the same size is not a self transfer`() {
        // Same amount, but the counterpart is money coming in from a person —
        // there is no second card of the user's involved.
        val purchase = tx(1, 50_000.0, TxType.PURCHASE, "Kaspi Gold")
        val fromFriend = tx(2, 50_000.0, TxType.TRANSFER_IN, null, offsetMs = 2_000)

        assertNull(SelfTransfer.matchIn(purchase, listOf(fromFriend), ownAccounts))
    }

    @Test
    fun `two halves must sit on different cards`() {
        val out = tx(1, 10_000.0, TxType.TRANSFER_OUT, "Kaspi Gold")
        val backOnTheSameCard = tx(2, 10_000.0, TxType.TRANSFER_IN, "Kaspi Gold", offsetMs = 1_000)

        assertNull(SelfTransfer.matchIn(out, listOf(backOnTheSameCard), ownAccounts))
    }

    @Test
    fun `the same amount hours apart is a coincidence, not a transfer`() {
        val out = tx(1, 20_000.0, TxType.TRANSFER_OUT, "Kaspi Gold")
        val late = tx(
            2, 20_000.0, TxType.TRANSFER_IN, "Kaspi Депозит",
            offsetMs = SelfTransfer.WINDOW_MS + 1_000
        )

        assertNull(SelfTransfer.matchIn(out, listOf(late), ownAccounts))
    }

    @Test
    fun `both directions have to differ`() {
        val first = tx(1, 30_000.0, TxType.TRANSFER_OUT, "Kaspi Gold")
        val alsoOutgoing = tx(2, 30_000.0, TxType.TRANSFER_OUT, "Kaspi Депозит", offsetMs = 1_000)

        assertNull(SelfTransfer.matchIn(first, listOf(alsoOutgoing), ownAccounts))
    }

    @Test
    fun `a card that is not the user's does not count`() {
        val out = tx(1, 15_000.0, TxType.TRANSFER_OUT, "Kaspi Gold")
        val elsewhere = tx(2, 15_000.0, TxType.TRANSFER_IN, "Halyk Card", offsetMs = 1_000)

        assertNull(SelfTransfer.matchIn(out, listOf(elsewhere), setOf("Kaspi Gold")))
    }

    @Test
    fun `each row is paired only once`() {
        // Two identical moves in the same minute must produce two pairs, not
        // four — otherwise one leg would be double-counted as excluded.
        val all = listOf(
            tx(1, 5_000.0, TxType.TRANSFER_OUT, "Kaspi Gold"),
            tx(2, 5_000.0, TxType.TRANSFER_IN, "Kaspi Депозит", offsetMs = 2_000),
            tx(3, 5_000.0, TxType.TRANSFER_OUT, "Kaspi Gold", offsetMs = 30_000),
            tx(4, 5_000.0, TxType.TRANSFER_IN, "Kaspi Депозит", offsetMs = 32_000)
        )

        val pairs = SelfTransfer.findAll(all, ownAccounts)
        assertEquals(2, pairs.size)
        val touched = pairs.flatMap { listOf(it.first.id, it.second.id) }
        assertEquals(touched.size, touched.toSet().size)
    }

    @Test
    fun `ordinary spending yields no pairs at all`() {
        val all = listOf(
            tx(1, 533.0, TxType.PURCHASE, "Kaspi Gold"),
            tx(2, 1_240.0, TxType.PURCHASE, "Kaspi Gold", offsetMs = 60_000),
            tx(3, 200_000.0, TxType.TRANSFER_IN, "Kaspi Gold", offsetMs = 120_000)
        )
        assertTrue(SelfTransfer.findAll(all, ownAccounts).isEmpty())
    }

    @Test
    fun `own cards are learned from the operations themselves`() {
        val all = listOf(
            tx(1, 100.0, TxType.PURCHASE, "Kaspi Gold"),
            tx(2, 100.0, TxType.PURCHASE, "Kaspi Депозит"),
            tx(3, 100.0, TxType.PURCHASE, null)
        )
        assertEquals(ownAccounts, SelfTransfer.ownAccountsIn(all))
    }
}
