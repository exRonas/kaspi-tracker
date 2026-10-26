package com.kleinapps.kaspitracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The partner's operation, as pulled from the cloud. Read-only on this phone
 * and kept apart from [Transaction] so nothing that works on the user's own
 * history (dedup, review queue, subscriptions, self-transfers) ever sees it.
 */
@Entity(tableName = "partner_transactions")
data class PartnerTransaction(
    @PrimaryKey val uuid: String,
    val timestamp: Long,
    val amount: Double?,
    val type: TxType,
    val merchant: String?,
    val category: Category?,
    val account: String?,
    val source: TxSource,
    val shared: Boolean,
    val excludedFromStats: Boolean
)

/** The same row in the shape every screen already knows how to draw. */
fun PartnerTransaction.toTransaction(): Transaction = Transaction(
    id = 0,
    timestamp = timestamp,
    title = null,
    rawText = "",
    amount = amount,
    type = type,
    merchant = merchant,
    source = source,
    category = category,
    account = account,
    excludedFromStats = excludedFromStats,
    uuid = uuid,
    shared = shared
)

@Dao
interface PartnerTransactionDao {

    @Upsert
    suspend fun upsertAll(rows: List<PartnerTransaction>)

    @Query("DELETE FROM partner_transactions WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM partner_transactions")
    suspend fun clear()

    @Query("SELECT * FROM partner_transactions WHERE amount IS NOT NULL ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<PartnerTransaction>>
}
