package com.kleinapps.kaspitracker.sync

import com.kleinapps.kaspitracker.data.FakePartnerTransactionDao
import com.kleinapps.kaspitracker.data.FakePendingDeleteDao
import com.kleinapps.kaspitracker.data.FakeTransactionDao
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TxSource
import com.kleinapps.kaspitracker.data.TxType
import com.kleinapps.kaspitracker.data.fakeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class SyncEngineTest {

    private lateinit var dao: FakeTransactionDao
    private lateinit var partnerDao: FakePartnerTransactionDao
    private lateinit var pendingDeletes: FakePendingDeleteDao
    private lateinit var cloud: FakeCloud
    private lateinit var cursor: SyncCursorStore
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        dao = FakeTransactionDao()
        partnerDao = FakePartnerTransactionDao()
        pendingDeletes = FakePendingDeleteDao()
        cloud = FakeCloud()
        cursor = object : SyncCursorStore {
            override var cursor: PullCursor? = null
        }
        engine = SyncEngine(dao, partnerDao, pendingDeletes, cloud, cursor, pageSize = 1000)
    }

    private fun tx(merchant: String = "Magnum", amount: Double? = 500.0) = Transaction(
        timestamp = 1_000L, title = "Kaspi Gold", rawText = "Покупка: 500 ₸\n$merchant",
        amount = amount, type = TxType.PURCHASE, merchant = merchant,
        source = TxSource.NOTIFICATION, account = "Kaspi Gold", balance = 1_200.0
    )

    private fun partnerRow(i: Int, deleted: Boolean = false) = CloudTransaction(
        uuid = "p-%05d".format(i), timestamp = i.toLong(), amount = 100.0, type = TxType.PURCHASE,
        merchant = "Shop $i", category = null, account = null,
        source = TxSource.NOTIFICATION, shared = i % 2 == 0, excludedFromStats = false,
        deleted = deleted
    )

    private val mine get() = cloud.rows.getValue("me")

    @Test
    fun `only changed rows are pushed`() = runTest {
        dao.insert(tx("Magnum"))
        dao.insert(tx("Coffee Boom"))

        val first = engine.sync() as SyncResult.Synced
        val second = engine.sync() as SyncResult.Synced

        assertEquals(2, first.pushed)
        assertEquals(0, second.pushed)
        assertEquals(2, mine.size)
    }

    @Test
    fun `editing a synced row pushes it again`() = runTest {
        val id = dao.insert(tx("Magnum"))
        engine.sync()
        dao.update(dao.getById(id)!!.copy(shared = true))

        val result = engine.sync() as SyncResult.Synced

        assertEquals(1, result.pushed)
        assertTrue(mine.values.single().shared)
    }

    @Test
    fun `amount-less pushes never leave the phone`() = runTest {
        dao.insert(tx("Реклама", amount = null))
        engine.sync()
        assertTrue(mine.isEmpty())
    }

    @Test
    fun `pushed rows keep what the partner needs`() = runTest {
        val id = dao.insert(tx("Magnum").copy(shared = true, excludedFromStats = true))
        engine.sync()

        val sent = mine.values.single()
        val local = dao.getById(id)!!
        assertEquals(local.uuid, sent.uuid)
        assertEquals(true, sent.shared)
        assertEquals(true, sent.excludedFromStats)
        assertEquals("Kaspi Gold", sent.account)
    }

    @Test
    fun `partner rows land in the partner table`() = runTest {
        cloud.partnerPushes(listOf(partnerRow(1), partnerRow(2)))

        val result = engine.sync() as SyncResult.Synced

        assertEquals(2, result.pulled)
        assertEquals(setOf("p-00001", "p-00002"), partnerDao.rows.value.map { it.uuid }.toSet())
    }

    @Test
    fun `a big first upload arrives whole across pages`() = runTest {
        cloud.partnerPushes((1..2_500).map { partnerRow(it) })

        engine.sync()

        assertEquals(2_500, partnerDao.rows.value.size)
    }

    @Test
    fun `later syncs pull only what changed since`() = runTest {
        cloud.partnerPushes(listOf(partnerRow(1)))
        engine.sync()
        cloud.partnerPushes(listOf(partnerRow(2)))

        val result = engine.sync() as SyncResult.Synced

        assertEquals(1, result.pulled)
    }

    @Test
    fun `partner deletion removes the row here`() = runTest {
        cloud.partnerPushes(listOf(partnerRow(1), partnerRow(2)))
        engine.sync()
        cloud.partnerPushes(listOf(partnerRow(1, deleted = true)))

        engine.sync()

        assertEquals(listOf("p-00002"), partnerDao.rows.value.map { it.uuid })
    }

    @Test
    fun `queued deletes are sent once and cleared`() = runTest {
        val repo = fakeRepository(dao, pendingDeleteDao = pendingDeletes)
        val id = repo.insert(tx("Magnum"))
        engine.sync()

        repo.delete(dao.getById(id)!!)
        engine.sync()

        assertTrue(mine.values.single().deleted)
        assertTrue(pendingDeletes.rows.value.isEmpty())
    }

    @Test
    fun `partner unpaired wipes partner data and resets sync state`() = runTest {
        dao.insert(tx("Magnum"))
        cloud.partnerPushes(listOf(partnerRow(1)))
        engine.sync()
        cloud.paired = false

        val result = engine.sync()

        assertEquals(SyncResult.Unpaired, result)
        assertTrue(partnerDao.rows.value.isEmpty())
        assertTrue(dao.rows.value.all { it.syncedHash == null })
        assertNull(cursor.cursor)
    }

    @Test
    fun `a failed push leaves rows dirty for the next try`() = runTest {
        dao.insert(tx("Magnum"))
        cloud.failPush = CloudError.NETWORK

        try {
            engine.sync()
            fail("expected the network error to surface")
        } catch (e: CloudException) {
            assertEquals(CloudError.NETWORK, e.error)
        }

        assertNull(dao.rows.value.single().syncedHash)
        cloud.failPush = null
        assertEquals(1, (engine.sync() as SyncResult.Synced).pushed)
    }

    @Test
    fun `partner profile comes along`() = runTest {
        cloud.profiles["partner"] = Profile("Аня", 2)
        assertEquals(Profile("Аня", 2), (engine.sync() as SyncResult.Synced).partner)
    }
}
