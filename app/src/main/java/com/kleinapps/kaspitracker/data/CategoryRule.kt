package com.kleinapps.kaspitracker.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * A category the user picked for a merchant, remembered so the same shop is
 * classified automatically next time instead of landing in the review queue
 * again ("Inmart", "SMALL", "ИП Старина", ...).
 *
 * [merchantKey] is the normalized merchant name — see [MerchantKey].
 */
@Entity(tableName = "category_rules")
data class CategoryRule(
    @PrimaryKey val merchantKey: String,
    val category: Category,
    val updatedAt: Long = System.currentTimeMillis()
)

@Dao
interface CategoryRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CategoryRule)

    @Query("SELECT * FROM category_rules")
    suspend fun getAll(): List<CategoryRule>

    @Query("SELECT * FROM category_rules ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CategoryRule>>

    @Query("DELETE FROM category_rules WHERE merchantKey = :merchantKey")
    suspend fun delete(merchantKey: String)
}

/**
 * Normalizes a merchant/counterparty string into a stable key.
 *
 * The same shop shows up with different spellings and decorations across
 * notifications and receipts ("SMALL" / "Small", `ТОО "Скиф трейд"`,
 * "ИП САДЫГОВ К.Н."), and some counterparties carry a number that is different
 * on every single receipt ("№ квитанции QR16793561441" for public transport).
 * Long digit runs are dropped and legal-form prefixes stripped so those all
 * collapse onto one key; strings that are left with nothing meaningful return
 * null, meaning "not usable as a rule key".
 */
object MerchantKey {

    /** Legal forms — the same shop appears with and without them. */
    private val LEGAL_FORMS = setOf(
        "ип", "тоо", "ао", "оао", "зао", "чп", "кх", "ооо", "оо", "пк", "кфх",
        "llp", "llc", "ltd", "too", "ip"
    )

    /** Receipt-number labels; whatever follows is unique per receipt. */
    private val NOISE = setOf(
        "квитанции", "квитанция", "квитанц", "чека", "чек", "no", "qr", "id",
        "идентификатор", "номер"
    )

    private const val MIN_KEY_LENGTH = 3

    fun of(merchant: String?): String? {
        if (merchant.isNullOrBlank()) return null

        val cleaned = merchant
            .lowercase()
            .replace('ё', 'е')
            // Receipt / QR / document numbers change on every payment.
            .replace(Regex("\\d{3,}"), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

        val tokens = cleaned.split(' ')
            .filter { it.isNotBlank() && it !in LEGAL_FORMS && it !in NOISE && !it.all(Char::isDigit) }

        val key = tokens.joinToString(" ")
        return key.takeIf { it.length >= MIN_KEY_LENGTH }
    }
}
