package com.kleinapps.kaspitracker.sync

import android.content.Context
import com.kleinapps.kaspitracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime

/** Supabase project keys, baked in from local.properties at build time. */
object CloudConfig {
    val url: String get() = BuildConfig.SUPABASE_URL.trimEnd('/')
    val anonKey: String get() = BuildConfig.SUPABASE_ANON_KEY
    val isConfigured: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()
}

/**
 * [CloudApi] over Supabase's plain HTTP endpoints (GoTrue for the anonymous
 * session, PostgREST for tables and functions). No client library: the app
 * needs about ten calls, and the official Kotlin client requires a newer
 * Kotlin than the project builds with.
 *
 * What a phone may read or write is decided by the row-level security in
 * supabase/schema.sql, not here.
 */
class SupabaseCloud(context: Context) : CloudApi {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val sessionLock = Mutex()

    private data class Session(val access: String, val refresh: String, val expiresAt: Long, val userId: String)

    // ------------------------------------------------------------- session

    override suspend fun ensureSignedIn(): String = session().userId

    /** A valid session: the stored one, refreshed when about to expire, or a new anonymous user. */
    private suspend fun session(): Session = sessionLock.withLock {
        if (!CloudConfig.isConfigured) throw CloudException(CloudError.NOT_CONFIGURED)
        val stored = storedSession()
        val fresh = when {
            stored == null -> authRequest("/auth/v1/signup", JSONObject().put("data", JSONObject()))
            stored.expiresAt - REFRESH_MARGIN_MS < System.currentTimeMillis() ->
                authRequest(
                    "/auth/v1/token?grant_type=refresh_token",
                    JSONObject().put("refresh_token", stored.refresh)
                )
            else -> return@withLock stored
        }
        fresh.also(::storeSession)
    }

    private suspend fun authRequest(path: String, body: JSONObject): Session {
        val json = JSONObject(http("POST", path, body.toString(), token = null) as String)
        return Session(
            access = json.getString("access_token"),
            refresh = json.getString("refresh_token"),
            expiresAt = System.currentTimeMillis() + json.getLong("expires_in") * 1000,
            userId = json.getJSONObject("user").getString("id")
        )
    }

    private fun storedSession(): Session? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        return Session(
            access = access,
            refresh = prefs.getString(KEY_REFRESH, null) ?: return null,
            expiresAt = prefs.getLong(KEY_EXPIRES, 0),
            userId = prefs.getString(KEY_USER, null) ?: return null
        )
    }

    private fun storeSession(session: Session) {
        prefs.edit()
            .putString(KEY_ACCESS, session.access)
            .putString(KEY_REFRESH, session.refresh)
            .putLong(KEY_EXPIRES, session.expiresAt)
            .putString(KEY_USER, session.userId)
            .apply()
    }

    // ------------------------------------------------------------- pairing

    override suspend fun createPairCode(): PairCode {
        val json = JSONObject(rest("POST", "/rest/v1/rpc/create_pair_code", "{}") as String)
        return PairCode(
            code = json.getString("code"),
            expiresAt = OffsetDateTime.parse(json.getString("expires_at")).toInstant().toEpochMilli()
        )
    }

    override suspend fun redeemPairCode(code: String) {
        val body = JSONObject().put("p_code", code).toString()
        val status = (rest("POST", "/rest/v1/rpc/redeem_pair_code", body) as String).trim('"', ' ', '\n')
        if (status != "ok") throw CloudException(errorFor(status))
    }

    override suspend fun partnerId(): String? {
        val me = session().userId
        val couples = JSONArray(rest("GET", "/rest/v1/couples?select=user_a,user_b") as String)
        if (couples.length() == 0) return null
        val couple = couples.getJSONObject(0)
        return couple.getString("user_a").takeIf { it != me } ?: couple.getString("user_b")
    }

    override fun signOut() {
        prefs.edit().clear().apply()
    }

    override suspend fun unpair() {
        rest("POST", "/rest/v1/rpc/unpair", "{}")
    }

    override suspend fun saveProfile(profile: Profile) {
        val body = JSONObject()
            .put("user_id", session().userId)
            .put("display_name", profile.name)
            .put("color", profile.color)
        rest("POST", "/rest/v1/profiles", body.toString(), prefer = "resolution=merge-duplicates,return=minimal")
    }

    override suspend fun profile(userId: String): Profile? {
        val rows = JSONArray(
            rest("GET", "/rest/v1/profiles?select=display_name,color&user_id=eq.${enc(userId)}") as String
        )
        if (rows.length() == 0) return null
        val row = rows.getJSONObject(0)
        return Profile(row.optString("display_name"), row.optInt("color"))
    }

    // ------------------------------------------------------------- rows

    override suspend fun push(rows: List<CloudTransaction>) {
        if (rows.isEmpty()) return
        val body = JSONArray().apply { rows.forEach { put(it.toJson()) } }.toString()
        rest(
            "POST",
            "/rest/v1/transactions?on_conflict=user_id,id",
            body,
            prefer = "resolution=merge-duplicates,return=minimal"
        )
    }

    override suspend fun markDeleted(uuids: List<String>) {
        if (uuids.isEmpty()) return
        val body = JSONObject().put("ids", JSONArray(uuids)).toString()
        rest("POST", "/rest/v1/rpc/mark_deleted", body)
    }

    override suspend fun pull(partnerId: String, after: PullCursor?, limit: Int): List<CloudTransaction> {
        val query = buildString {
            append("/rest/v1/transactions?select=*")
            append("&user_id=eq.").append(enc(partnerId))
            append("&order=updated_at.asc,id.asc&limit=").append(limit)
            if (after != null) {
                // Keyset paging: strictly after (updated_at, id).
                append("&or=").append(
                    enc(
                        "(updated_at.gt.\"${after.updatedAt}\"," +
                            "and(updated_at.eq.\"${after.updatedAt}\",id.gt.${after.uuid}))"
                    )
                )
            }
        }
        val rows = JSONArray(rest("GET", query) as String)
        return (0 until rows.length()).map { cloudTransactionFrom(rows.getJSONObject(it)) }
    }

    // ------------------------------------------------------------- http

    private suspend fun rest(method: String, path: String, body: String? = null, prefer: String? = null): Any? =
        http(method, path, body, token = session().access, prefer = prefer)

    private suspend fun http(
        method: String,
        path: String,
        body: String?,
        token: String?,
        prefer: String? = null
    ): Any? = withContext(Dispatchers.IO) {
        val connection = try {
            (URL(CloudConfig.url + path).openConnection() as HttpURLConnection)
        } catch (e: IOException) {
            throw CloudException(CloudError.NETWORK, e.message)
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("apikey", CloudConfig.anonKey)
            // Only a user's session goes in Authorization. Sign-up and refresh send
            // the project key as `apikey` alone, which both the legacy anon JWT and
            // the newer sb_publishable_ keys accept.
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            prefer?.let { connection.setRequestProperty("Prefer", it) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw CloudException(errorForHttp(code, text), "HTTP $code: $text")
            text.ifEmpty { null }
        } catch (e: IOException) {
            throw CloudException(CloudError.NETWORK, e.message)
        } finally {
            connection.disconnect()
        }
    }

    private fun errorForHttp(code: Int, body: String): CloudError {
        val message = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
        return when {
            code >= 500 -> CloudError.SERVER_UNAVAILABLE
            message.isNotBlank() && errorFor(message) != CloudError.UNKNOWN -> errorFor(message)
            else -> CloudError.UNKNOWN
        }
    }

    private fun errorFor(status: String): CloudError = when (status) {
        "code_invalid" -> CloudError.CODE_INVALID
        "code_expired" -> CloudError.CODE_EXPIRED
        "own_code" -> CloudError.OWN_CODE
        "already_paired" -> CloudError.ALREADY_PAIRED
        "too_many_attempts" -> CloudError.TOO_MANY_ATTEMPTS
        else -> CloudError.UNKNOWN
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val PREFS = "supabase_session"
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "expires_at"
        const val KEY_USER = "user_id"
        const val REFRESH_MARGIN_MS = 60_000L
        const val TIMEOUT_MS = 15_000
    }
}
