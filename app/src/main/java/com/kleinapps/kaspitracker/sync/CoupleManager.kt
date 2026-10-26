package com.kleinapps.kaspitracker.sync

import android.content.Context
import com.kleinapps.kaspitracker.data.AppDatabase
import com.kleinapps.kaspitracker.data.ViewScope
import com.kleinapps.kaspitracker.ui.theme.PersonTone
import com.kleinapps.kaspitracker.ui.theme.ThemeController

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
    suspend fun redeem(code: String) {
        cloud.ensureSignedIn()
        cloud.saveProfile(Profile(state.myName, myColor))
        cloud.redeemPairCode(code)
        holder.update { it.copy(paired = true, pendingCode = null, lastError = null) }
        syncNow()
    }

    /** True once the partner has entered this phone's code. */
    suspend fun checkPending(): Boolean {
        if (state.pendingCode == null) return state.paired
        if (cloud.partnerId() == null) return false
        holder.update { it.copy(paired = true, pendingCode = null, lastError = null) }
        syncNow()
        return true
    }

    /** One sync round. No-op while unpaired; errors are recorded, never thrown. */
    suspend fun syncNow() {
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
        }
    }

    /** Ends the couple for both phones and forgets the partner's data here. */
    suspend fun unpair() {
        cloud.unpair()
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
