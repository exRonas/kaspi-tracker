package com.kleinapps.kaspitracker.sync

/**
 * In-memory cloud for two users, "me" and "partner", behaving like the
 * Supabase side: rows keyed by uuid per owner, a server clock that is the same
 * for every row of one request, and keyset paging on (updatedAt, uuid).
 */
class FakeCloud(
    private val me: String = "me",
    private val partner: String = "partner"
) : CloudApi {

    /** owner id -> uuid -> row */
    val rows = mutableMapOf(me to mutableMapOf<String, CloudTransaction>(), partner to mutableMapOf())
    var paired = true
    val profiles = mutableMapOf<String, Profile>()
    var failPush: CloudError? = null
    var codes = mutableMapOf<String, String>()
    var pushCalls = 0
    private var clock = 0

    /** Fixed width so string order equals time order, like ISO timestamps. */
    private fun tick(): String = (++clock).toString().padStart(8, '0')

    /** The partner's phone uploading [batch] in one request. */
    fun partnerPushes(batch: List<CloudTransaction>) = write(partner, batch)

    private fun write(owner: String, batch: List<CloudTransaction>) {
        val now = tick()
        batch.forEach { rows.getValue(owner)[it.uuid] = it.copy(updatedAt = now) }
    }

    override suspend fun ensureSignedIn(): String = me

    override suspend fun createPairCode(): PairCode {
        codes["ABCDEFGH"] = me
        return PairCode("ABCDEFGH", Long.MAX_VALUE)
    }

    override suspend fun redeemPairCode(code: String) {
        if (codes[code.replace("-", "").uppercase()] == null) throw CloudException(CloudError.CODE_INVALID)
        paired = true
    }

    override suspend fun partnerId(): String? = if (paired) partner else null

    override suspend fun unpair() {
        paired = false
        rows.values.forEach { it.clear() }
    }

    override suspend fun saveProfile(profile: Profile) {
        profiles[me] = profile
    }

    override suspend fun profile(userId: String): Profile? = profiles[userId]

    override suspend fun push(rows: List<CloudTransaction>) {
        pushCalls++
        failPush?.let { throw CloudException(it) }
        write(me, rows)
    }

    override suspend fun markDeleted(uuids: List<String>) {
        val now = tick()
        uuids.forEach { id ->
            rows.getValue(me)[id]?.let { rows.getValue(me)[id] = it.copy(deleted = true, updatedAt = now) }
        }
    }

    override suspend fun pull(partnerId: String, after: PullCursor?, limit: Int): List<CloudTransaction> =
        rows.getValue(partnerId).values
            .sortedWith(compareBy({ it.updatedAt }, { it.uuid }))
            .filter { row ->
                after == null || row.updatedAt!! > after.updatedAt ||
                    (row.updatedAt == after.updatedAt && row.uuid > after.uuid)
            }
            .take(limit)
}
