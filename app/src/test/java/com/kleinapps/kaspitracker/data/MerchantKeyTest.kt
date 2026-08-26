package com.kleinapps.kaspitracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantKeyTest {

    @Test
    fun `same shop written differently collapses onto one key`() {
        assertEquals(MerchantKey.of("SMALL"), MerchantKey.of("Small"))
        assertEquals(MerchantKey.of("Inmart"), MerchantKey.of("  inmart "))
        assertEquals(MerchantKey.of("ТОО \"Скиф трейд\""), MerchantKey.of("Скиф Трейд"))
        assertEquals(MerchantKey.of("ИП САДЫГОВ К.Н."), MerchantKey.of("Садыгов К. Н."))
    }

    @Test
    fun `different shops keep different keys`() {
        val keys = listOf("SMALL", "Inmart", "Magnum", "Greenwich").map { MerchantKey.of(it) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `receipt numbers are not usable as a key`() {
        // Public transport receipts name a per-ride number as the counterparty —
        // learning a rule from those would create a new merchant every ride.
        assertNull(MerchantKey.of("№ квитанции QR16793561441"))
        assertNull(MerchantKey.of("№ чека QR16790207628"))
        assertNull(MerchantKey.of("870914906035266706"))
    }

    @Test
    fun `blank and too short input yields no key`() {
        assertNull(MerchantKey.of(null))
        assertNull(MerchantKey.of(""))
        assertNull(MerchantKey.of("   "))
        assertNull(MerchantKey.of("ИП"))
    }

    @Test
    fun `yo is normalized so both spellings match`() {
        assertEquals(MerchantKey.of("Пётр"), MerchantKey.of("Петр"))
    }
}
