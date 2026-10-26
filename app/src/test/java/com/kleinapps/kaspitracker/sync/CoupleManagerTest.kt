package com.kleinapps.kaspitracker.sync

import com.kleinapps.kaspitracker.data.FakePartnerTransactionDao
import com.kleinapps.kaspitracker.data.FakePendingDeleteDao
import com.kleinapps.kaspitracker.data.FakeTransactionDao
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TxSource
import com.kleinapps.kaspitracker.data.TxType
import com.kleinapps.kaspitracker.data.ViewScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class CoupleManagerTest {

    private class MemoryHolder(initial: CoupleState = CoupleState()) : CoupleStateHolder {
        private val flow = MutableStateFlow(initial)
        override val state: StateFlow<CoupleState> get() = flow
        override fun update(block: (CoupleState) -> CoupleState) {
            flow.value = block(flow.value)
        }
    }

    private lateinit var dao: FakeTransactionDao
    private lateinit var partnerDao: FakePartnerTransactionDao
    private lateinit var cloud: FakeCloud
    private lateinit var holder: MemoryHolder
    private lateinit var manager: CoupleManager

    @Before
    fun setUp() {
        dao = FakeTransactionDao()
        partnerDao = FakePartnerTransactionDao()
        cloud = FakeCloud().apply { paired = false }
        holder = MemoryHolder()
        val cursor = object : SyncCursorStore {
            override var cursor: PullCursor? = null
        }
        val engine = SyncEngine(dao, partnerDao, FakePendingDeleteDao(), cloud, cursor)
        manager = CoupleManager(cloud, engine, holder, now = { 42L })
    }

    private fun partnerRow(id: String) = CloudTransaction(
        uuid = id, timestamp = 1, amount = 100.0, type = TxType.PURCHASE, merchant = "Shop",
        category = null, account = null, source = TxSource.NOTIFICATION, shared = true,
        excludedFromStats = false
    )

    private fun paired() {
        cloud.paired = true
        holder.update { it.copy(paired = true, scope = ViewScope.TOGETHER) }
    }

    @Test
    fun `redeeming a code pairs and pulls partner data`() = runTest {
        cloud.codes["ABCDEFGH"] = "partner"
        cloud.profiles["partner"] = Profile("Аня", 0)
        cloud.partnerPushes(listOf(partnerRow("p1")))
        manager.saveProfile("Нурлан", 0)

        manager.redeem("abcd-efgh")

        val state = holder.state.value
        assertTrue(state.paired)
        assertEquals("Аня", state.partnerName)
        assertEquals(42L, state.lastSyncAt)
        assertEquals(listOf("p1"), partnerDao.rows.value.map { it.uuid })
        assertEquals(Profile("Нурлан", 0), cloud.profiles["me"])
    }

    @Test
    fun `wrong code reports the reason and stays unpaired`() = runTest {
        try {
            manager.redeem("ZZZZZZZZ")
            fail("expected the code to be refused")
        } catch (e: CloudException) {
            assertEquals(CloudError.CODE_INVALID, e.error)
        }
        assertFalse(holder.state.value.paired)
    }

    @Test
    fun `pending code turns into a pair when partner redeems it`() = runTest {
        val code = manager.createCode()
        assertEquals(code, holder.state.value.pendingCode)
        assertFalse(manager.checkPending())

        cloud.paired = true // the partner typed the code in
        assertTrue(manager.checkPending())

        assertTrue(holder.state.value.paired)
        assertNull(holder.state.value.pendingCode)
    }

    @Test
    fun `partner unpairing is noticed on next sync and scope falls back to me`() = runTest {
        paired()
        cloud.paired = false

        manager.syncNow()

        assertFalse(holder.state.value.paired)
        assertEquals(ViewScope.ME, holder.state.value.effectiveScope)
    }

    @Test
    fun `network failure is recorded and data is kept`() = runTest {
        paired()
        cloud.partnerPushes(listOf(partnerRow("p1")))
        manager.syncNow()
        dao.insert(
            Transaction(timestamp = 1, title = null, rawText = "", amount = 5.0, type = TxType.PURCHASE, merchant = "M")
        )
        cloud.failPush = CloudError.NETWORK

        manager.syncNow()

        assertEquals(CloudError.NETWORK, holder.state.value.lastError)
        assertTrue(holder.state.value.paired)
        assertEquals(1, partnerDao.rows.value.size)
    }

    @Test
    fun `a successful sync clears the last error`() = runTest {
        paired()
        holder.update { it.copy(lastError = CloudError.NETWORK) }

        manager.syncNow()

        assertNull(holder.state.value.lastError)
    }

    @Test
    fun `sync does nothing while unpaired`() = runTest {
        cloud.partnerPushes(listOf(partnerRow("p1")))
        manager.syncNow()
        assertTrue(partnerDao.rows.value.isEmpty())
    }

    @Test
    fun `unpair wipes partner rows locally`() = runTest {
        paired()
        cloud.partnerPushes(listOf(partnerRow("p1")))
        manager.syncNow()

        manager.unpair()

        assertTrue(partnerDao.rows.value.isEmpty())
        assertFalse(holder.state.value.paired)
        assertFalse(cloud.paired)
    }

    @Test
    fun `scope is remembered but only applies while paired`() {
        manager.setScope(ViewScope.PARTNER)
        assertEquals(ViewScope.PARTNER, holder.state.value.scope)
        assertEquals(ViewScope.ME, holder.state.value.effectiveScope)
    }
}
