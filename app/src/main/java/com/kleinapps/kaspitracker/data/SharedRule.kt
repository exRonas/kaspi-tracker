package com.kleinapps.kaspitracker.data

import android.content.Context
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

/**
 * The user's switch for [SharedRule] hints. On by default; off means every new
 * operation arrives as "own" and sharing is always marked by hand.
 */
object SharedHints {

    private const val PREFS = "settings"
    private const val KEY = "shared_hints"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

    fun setEnabled(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }
}
