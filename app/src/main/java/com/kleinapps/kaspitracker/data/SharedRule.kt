package com.kleinapps.kaspitracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Whether the user last marked this merchant's operations as shared or own,
 * so new ones from the same shop arrive pre-marked. Keyed like [CategoryRule].
 */
@Entity(tableName = "shared_rules")
data class SharedRule(
    @PrimaryKey val merchantKey: String,
    val shared: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface SharedRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: SharedRule)

    @Query("SELECT * FROM shared_rules")
    suspend fun getAll(): List<SharedRule>
}
