package com.kleinapps.kaspitracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * An operation deleted here after it had already reached the cloud. The row
 * itself is gone, so its uuid waits here until the deletion is sent too.
 */
@Entity(tableName = "pending_deletes")
data class PendingDelete(@PrimaryKey val uuid: String)

@Dao
interface PendingDeleteDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(row: PendingDelete)

    @Query("SELECT * FROM pending_deletes")
    suspend fun getAll(): List<PendingDelete>

    @Query("DELETE FROM pending_deletes WHERE uuid IN (:uuids)")
    suspend fun deleteByUuids(uuids: List<String>)

    @Query("DELETE FROM pending_deletes")
    suspend fun clear()
}
