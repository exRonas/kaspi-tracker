package com.kleinapps.kaspitracker.sync

import android.content.Context
import com.kleinapps.kaspitracker.data.AppDatabase
import com.kleinapps.kaspitracker.data.ViewScope
import com.kleinapps.kaspitracker.ui.theme.PersonTone
import com.kleinapps.kaspitracker.ui.theme.ThemeController
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Pairing and syncing as the screens see them: every action updates the
 * [CoupleStateHolder] so the UI follows, and failures end up in
 * [CoupleState.lastError] instead of crashing anything.
 */
class CoupleManager(
    private val cloud: CloudApi,
    private val engine: SyncEngine,
    private val holder: CoupleStateHolder,
    private val now: () -> Long = System::currentTimeMillis
) {

    private val state get() = holder.state.value

    /**
     * One cloud conversation at a time. The periodic worker, the "soon" worker,
     * the Sync button and the code poll can all fire together; without this a
     * pull still running after an unpair writes the partner's rows back.
     */
    private val lock = Mutex()

    /**
     * Keeps the name locally; while paired also sends it (and the colour) to the
     * partner. Before pairing it goes up with [createCode] / [redeem].
     */
    suspend fun saveProfile(name: String, color: Int) {
        holder.update { it.copy(myName = name.trim()) }
        if (state.paired) cloud.saveProfile(Profile(state.myName, color))
        myColor = color
    }

    /** A fresh code for the partner to type in; the screen then polls [checkPending]. */
    suspend fun createCode(): PairCode {
        cloud.ensureSignedIn()
        cloud.saveProfile(Profile(state.myName, myColor))
        val code = cloud.createPairCode()
        holder.update { it.copy(pendingCode = code, lastError = null) }
        return code
    }

    /** Pairs with the owner of [code], then pulls their data. Throws with the reason on a bad code. */
    suspend fun redeem(code: String) = lock.withLock {
        cloud.ensureSignedIn()
        cloud.saveProfile(Profile(state.myName, myColor))
        try {
            cloud.redeemPairCode(code)
        } catch (e: CloudException) {
            // The server may have paired us while the answer was lost; a retry
            // then hears "already paired" about our own pair.
            if (e.error != CloudError.ALREADY_PAIRED || cloud.partnerId() == null) throw e
        }
        startPairing()
    }

    /** True once the partner has entered this phone's code. */
    suspend fun checkPending(): Boolean = lock.withLock {
        if (state.pendingCode == null) return@withLock state.paired
        if (cloud.partnerId() == null) return@withLock false
        startPairing()
        true
    }

    /**
     * A new pair starts from a clean slate: whatever this phone thinks it sent
     * before (a restored backup can carry sync marks) is forgotten, so the
     * whole history goes up to the new partner.
     */
    private suspend fun startPairing() {
        engine.wipePartnerData()
        holder.update { it.copy(paired = true, pendingCode = null, lastError = null) }
        syncLocked()
    }

    /** One sync round. No-op while unpaired; errors are recorded, never thrown. */
    suspend fun syncNow() = lock.withLock { syncLocked() }

    private suspend fun syncLocked() {
        if (!state.paired) return
        try {
            when (val result = engine.sync()) {
                is SyncResult.Synced -> holder.update {
                    it.copy(
                        partnerName = result.partner?.name ?: it.partnerName,
                        partnerColor = result.partner?.color ?: it.partnerColor,
                        lastSyncAt = now(),
                        lastError = null
                    )
                }
                SyncResult.Unpaired -> holder.update { it.copy(paired = false, scope = ViewScope.ME, lastError = null) }
            }
        } catch (e: CloudException) {
            holder.update { it.copy(lastError = e.error) }
        } catch (e: Exception) {
            // An answer the client could not read: record it, never crash the app.
            holder.update { it.copy(lastError = CloudError.UNKNOWN) }
        }
    }

    /** Ends the couple for both phones and forgets the partner's data here. */
    suspend fun unpair() = lock.withLock {
        try {
            cloud.unpair()
        } catch (e: CloudException) {
            // Offline: keep the pair so the user can retry — the partner would
            // otherwise keep seeing our data. Any other refusal (a dead session
            // after a phone restore) must not trap the user: unpair here and
            // start the next pairing as a new anonymous user.
            if (e.error == CloudError.NETWORK || e.error == CloudError.SERVER_UNAVAILABLE) throw e
            cloud.signOut()
        }
        engine.wipePartnerData()
        holder.update {
            it.copy(paired = false, partnerName = "", partnerColor = -1, scope = ViewScope.ME, lastError = null)
        }
    }

    fun setScope(scope: ViewScope) {
        holder.update { it.copy(scope = scope) }
    }

    /** The user's own colour index; owned by ThemeController in the app, kept here for tests. */
    private var myColor: Int = 0

    companion object {
        @Volatile
        private var INSTANCE: CoupleManager? = null

        fun get(context: Context): CoupleManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val app = context.applicationContext
                    val db = AppDatabase.getInstance(app)
                    val cloud = SupabaseCloud(app)
                    val engine = SyncEngine(
                        dao = db.transactionDao(),
                        partnerDao = db.partnerTransactionDao(),
                        pendingDeleteDao = db.pendingDeleteDao(),
                        cloud = cloud,
                        cursorStore = PrefsCursorStore(app)
                    )
                    CoupleManager(cloud, engine, CoupleStore).also {
                        it.myColor = PersonTone.choices.indexOf(ThemeController.tone.value).coerceAtLeast(0)
                        INSTANCE = it
                    }
                }
            }
    }
}

/** Pull cursor in SharedPreferences, next to the rest of the sync state. */
private class PrefsCursorStore(context: Context) : SyncCursorStore {
    private val prefs = context.getSharedPreferences("sync_cursor", Context.MODE_PRIVATE)

    override var cursor: PullCursor?
        get() {
            val at = prefs.getString("updated_at", null) ?: return null
            val id = prefs.getString("id", null) ?: return null
            return PullCursor(at, id)
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) clear() else putString("updated_at", value.updatedAt).putString("id", value.uuid)
            }.apply()
        }
}
