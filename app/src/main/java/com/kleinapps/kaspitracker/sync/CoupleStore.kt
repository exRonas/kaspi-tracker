package com.kleinapps.kaspitracker.sync

import android.content.Context
import android.content.SharedPreferences
import com.kleinapps.kaspitracker.data.ViewScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * Everything the app knows about the pairing, kept on the phone. The user's own
 * colour lives in ThemeController (it drives the theme even when unpaired).
 */
data class CoupleState(
    val paired: Boolean = false,
    val myName: String = "",
    val partnerName: String = "",
    /** Index into PersonTone.choices, as the partner picked it. */
    val partnerColor: Int = -1,
    /** A code this phone is showing, waiting for the partner to enter it. */
    val pendingCode: PairCode? = null,
    val lastSyncAt: Long = 0L,
    val lastError: CloudError? = null,
    /** The view the user last chose. */
    val scope: ViewScope = ViewScope.ME
) {
    /** What the screens actually show: without a partner there is only "me". */
    val effectiveScope: ViewScope get() = if (paired) scope else ViewScope.ME
}

interface CoupleStateHolder {
    val state: StateFlow<CoupleState>
    fun update(block: (CoupleState) -> CoupleState)
}

/** The app's [CoupleStateHolder], persisted as one JSON string in SharedPreferences. */
object CoupleStore : CoupleStateHolder {

    private const val PREFS = "couple"
    private const val KEY = "state"

    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(CoupleState())
    override val state: StateFlow<CoupleState> get() = _state

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _state.value = prefs.getString(KEY, null)?.let { runCatching { decode(it) }.getOrNull() } ?: CoupleState()
    }

    @Synchronized
    override fun update(block: (CoupleState) -> CoupleState) {
        val next = block(_state.value)
        _state.value = next
        if (::prefs.isInitialized) prefs.edit().putString(KEY, encode(next)).apply()
    }

    private fun encode(s: CoupleState): String = JSONObject().apply {
        put("paired", s.paired)
        put("myName", s.myName)
        put("partnerName", s.partnerName)
        put("partnerColor", s.partnerColor)
        s.pendingCode?.let { put("pendingCode", it.code); put("pendingExpires", it.expiresAt) }
        put("lastSyncAt", s.lastSyncAt)
        s.lastError?.let { put("lastError", it.name) }
        put("scope", s.scope.name)
    }.toString()

    private fun decode(json: String): CoupleState {
        val o = JSONObject(json)
        return CoupleState(
            paired = o.optBoolean("paired"),
            myName = o.optString("myName"),
            partnerName = o.optString("partnerName"),
            partnerColor = o.optInt("partnerColor", -1),
            pendingCode = if (o.has("pendingCode")) PairCode(o.getString("pendingCode"), o.optLong("pendingExpires")) else null,
            lastSyncAt = o.optLong("lastSyncAt"),
            lastError = o.optString("lastError").takeIf { it.isNotBlank() }
                ?.let { runCatching { CloudError.valueOf(it) }.getOrNull() },
            scope = runCatching { ViewScope.valueOf(o.optString("scope")) }.getOrDefault(ViewScope.ME)
        )
    }
}
