package com.kleinapps.kaspitracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Monthly spending limit for a single [Category]. */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: Category,
    val monthlyLimit: Double
)

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: Budget)

    @Delete
    suspend fun delete(budget: Budget)

    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<Budget>>

    /** Snapshot for backup export and for checking limits after an insert. */
    @Query("SELECT * FROM budgets")
    suspend fun getAllOnce(): List<Budget>
}
