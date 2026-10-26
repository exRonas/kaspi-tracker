package com.kleinapps.kaspitracker.sync

import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.TxSource
import com.kleinapps.kaspitracker.data.TxType

/**
 * An operation as the cloud stores it: what the partner needs to see, nothing
 * more. Raw notification and receipt text never leave the phone.
 */
data class CloudTransaction(
    val uuid: String,
    val timestamp: Long,
    val amount: Double?,
    val type: TxType,
    val merchant: String?,
    val category: Category?,
    val account: String?,
    val balance: Double?,
    val source: TxSource,
    val shared: Boolean,
    val excludedFromStats: Boolean,
    val deleted: Boolean = false,
    /** Server time of the last change; set by the cloud, null on the way up. */
    val updatedAt: String? = null
)

/**
 * Where the last pull stopped. Ordering is (updatedAt, uuid) so rows written
 * in the same instant — a whole history uploaded at once — are never skipped
 * at a page boundary.
 */
data class PullCursor(val updatedAt: String, val uuid: String)

data class PairCode(val code: String, val expiresAt: Long)

data class Profile(val name: String, val color: Int)

enum class CloudError {
    CODE_INVALID,
    CODE_EXPIRED,
    OWN_CODE,
    ALREADY_PAIRED,
    TOO_MANY_ATTEMPTS,
    NETWORK,
    SERVER_UNAVAILABLE,
    NOT_CONFIGURED,
    UNKNOWN
}

class CloudException(val error: CloudError, message: String? = null) : Exception(message ?: error.name)

/** Everything the app asks of the cloud. [SupabaseCloud] in the app, a fake in tests. */
interface CloudApi {
    /** Signs in anonymously if needed; returns this phone's user id. */
    suspend fun ensureSignedIn(): String
    suspend fun createPairCode(): PairCode
    /** Pairs with the code's owner; throws [CloudException] with the reason otherwise. */
    suspend fun redeemPairCode(code: String)
    /** The partner's user id, or null while this phone is not paired. */
    suspend fun partnerId(): String?
    suspend fun unpair()
    suspend fun saveProfile(profile: Profile)
    suspend fun profile(userId: String): Profile?
    suspend fun push(rows: List<CloudTransaction>)
    suspend fun markDeleted(uuids: List<String>)
    /** The partner's rows changed after [after], oldest first, at most [limit]. */
    suspend fun pull(partnerId: String, after: PullCursor?, limit: Int): List<CloudTransaction>
}
