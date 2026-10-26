package com.kleinapps.kaspitracker.sync

import com.kleinapps.kaspitracker.data.PartnerTransactionDao
import com.kleinapps.kaspitracker.data.PendingDeleteDao
import com.kleinapps.kaspitracker.data.TransactionDao

/** Remembers where the last pull of the partner's rows stopped. */
interface SyncCursorStore {
    var cursor: PullCursor?
}

sealed interface SyncResult {
    data class Synced(val partnerId: String, val partner: Profile?, val pushed: Int, val pulled: Int) : SyncResult
    data object Unpaired : SyncResult
}

/**
 * One round of exchange with the cloud: send my changes and deletions, fetch
 * the partner's. Conflicts can't happen — every row has exactly one writer,
 * its owner — so the engine only has to make sure nothing is missed or sent
 * twice, and that a failure halfway leaves the next round able to finish.
 */
class SyncEngine(
    private val dao: TransactionDao,
    private val partnerDao: PartnerTransactionDao,
    private val pendingDeleteDao: PendingDeleteDao,
    private val cloud: CloudApi,
    private val cursorStore: SyncCursorStore,
    private val pageSize: Int = 1000
) {

    suspend fun sync(): SyncResult {
        val partnerId = cloud.partnerId() ?: run {
            // The partner unpaired from their side (or the couple never existed).
            wipePartnerData()
            return SyncResult.Unpaired
        }
        // A failed upload must not also stop the partner's rows from arriving:
        // pull anyway, then report the upload error.
        var pushed = 0
        val pushError = try {
            pushed = pushChanges()
            pushDeletions()
            null
        } catch (e: CloudException) {
            e
        }
        val pulled = pullPartner(partnerId)
        pushError?.let { throw it }
        return SyncResult.Synced(partnerId, cloud.profile(partnerId), pushed, pulled)
    }

    /**
     * Forgets everything that came from or went to the partner: their rows go,
     * and mine count as never sent, so a future pairing uploads them afresh.
     */
    suspend fun wipePartnerData() {
        partnerDao.clear()
        dao.clearSyncState()
        pendingDeleteDao.clear()
        cursorStore.cursor = null
    }

    /** Rows whose cloud version is missing or stale. Each batch is marked only after it landed. */
    private suspend fun pushChanges(): Int {
        val dirty = dao.getAllOnce()
            .filter { it.amount != null }
            .map { it to it.syncHash() }
            .filter { (tx, hash) -> tx.syncedHash != hash }
        dirty.chunked(PUSH_BATCH).forEach { batch ->
            cloud.push(batch.map { (tx, _) -> tx.toCloud() })
            // The hash of what was sent: if the row changed meanwhile it stays dirty.
            batch.forEach { (tx, hash) -> dao.markSynced(tx.uuid, hash) }
        }
        return dirty.size
    }

    private suspend fun pushDeletions() {
        val uuids = pendingDeleteDao.getAll().map { it.uuid }
        if (uuids.isEmpty()) return
        uuids.chunked(PUSH_BATCH).forEach { cloud.markDeleted(it) }
        pendingDeleteDao.deleteByUuids(uuids)
    }

    /** Pages through the partner's changes; the cursor is saved after every page. */
    private suspend fun pullPartner(partnerId: String): Int {
        var pulled = 0
        while (true) {
            val page = cloud.pull(partnerId, cursorStore.cursor, pageSize)
            if (page.isEmpty()) break
            val (gone, alive) = page.partition { it.deleted }
            if (gone.isNotEmpty()) partnerDao.deleteByUuids(gone.map { it.uuid })
            if (alive.isNotEmpty()) partnerDao.upsertAll(alive.map { it.toPartner() })
            pulled += page.size
            val last = page.last()
            cursorStore.cursor = PullCursor(last.updatedAt ?: break, last.uuid)
            if (page.size < pageSize) break
        }
        return pulled
    }

    private companion object {
        const val PUSH_BATCH = 500
    }
}
