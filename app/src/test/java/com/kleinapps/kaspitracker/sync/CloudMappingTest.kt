package com.kleinapps.kaspitracker.sync

import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TxSource
import com.kleinapps.kaspitracker.data.TxType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CloudMappingTest {

    private val row = CloudTransaction(
        uuid = "7f0c1a2e-0000-4000-8000-000000000001", timestamp = 1_785_487_020_000,
        amount = 2_431.0, type = TxType.PURCHASE, merchant = "Inmart",
        category = Category.GROCERIES, account = "Kaspi Gold",
        source = TxSource.RECEIPT, shared = true, excludedFromStats = false
    )

    @Test
    fun `json round trip keeps every field`() {
        val json = row.toJson().put("updated_at", "2026-10-26T10:00:00.123456+00:00")
        val back = cloudTransactionFrom(JSONObject(json.toString()))

        assertEquals(row.copy(updatedAt = "2026-10-26T10:00:00.123456+00:00"), back)
    }

    @Test
    fun `nulls survive the round trip`() {
        val bare = row.copy(amount = null, merchant = null, category = null, account = null)
        assertEquals(bare, cloudTransactionFrom(JSONObject(bare.toJson().toString())))
    }

    @Test
    fun `outgoing json carries no owner or server time`() {
        val json = row.toJson()
        assertFalse(json.has("user_id"))
        assertFalse(json.has("updated_at"))
        assertEquals(row.uuid, json.getString("id"))
        assertEquals(true, json.getBoolean("shared"))
    }

    @Test
    fun `unknown category from server becomes null`() {
        val json = row.toJson().put("category", "SOMETHING_NEW")
        assertNull(cloudTransactionFrom(json).category)
    }

    @Test
    fun `sync hash changes with what the partner sees and ignores local-only text`() {
        val tx = Transaction(
            timestamp = 1, title = "t", rawText = "raw", amount = 10.0,
            type = TxType.PURCHASE, merchant = "M"
        )
        assertEquals(tx.syncHash(), tx.copy(rawText = "other raw", title = null).syncHash())
        assertNotEquals(tx.syncHash(), tx.copy(shared = true).syncHash())
        assertNotEquals(tx.syncHash(), tx.copy(category = Category.CAFE).syncHash())
    }

    @Test
    fun `sync hash is the same in every process`() {
        // Enum hashCode() is an identity hash that changes on every app start;
        // a hash built on it made every row look changed after a restart.
        val tx = Transaction(
            timestamp = 1_785_487_020_000, title = null, rawText = "", amount = 2_431.0,
            type = TxType.PURCHASE, merchant = "Inmart", source = TxSource.RECEIPT,
            category = Category.GROCERIES, account = "Kaspi Gold",
            uuid = "7f0c1a2e-0000-4000-8000-000000000001", shared = true
        )
        assertEquals(EXPECTED_HASH, tx.syncHash())
    }

    private companion object {
        // Pinned: a change here means every phone re-uploads its history once.
        const val EXPECTED_HASH = 1_811_927_737
    }
}
