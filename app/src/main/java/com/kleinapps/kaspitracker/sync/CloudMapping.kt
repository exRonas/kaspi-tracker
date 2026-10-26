package com.kleinapps.kaspitracker.sync

import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.data.PartnerTransaction
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TxSource
import com.kleinapps.kaspitracker.data.TxType
import org.json.JSONObject

/**
 * The part of an operation the partner gets. Raw text, title, dedup key and the
 * card balance stay on this phone.
 */
fun Transaction.toCloud(): CloudTransaction = CloudTransaction(
    uuid = uuid,
    timestamp = timestamp,
    amount = amount,
    type = type,
    merchant = merchant,
    category = category,
    account = account,
    source = source,
    shared = shared,
    excludedFromStats = excludedFromStats
)

/**
 * Fingerprint of what the cloud would hold for this row. Edits to fields that
 * never leave the phone don't change it, so they cause no upload.
 */
fun Transaction.syncHash(): Int = toCloud().hashCode()

fun CloudTransaction.toPartner(): PartnerTransaction = PartnerTransaction(
    uuid = uuid,
    timestamp = timestamp,
    amount = amount,
    type = type,
    merchant = merchant,
    category = category,
    account = account,
    source = source,
    shared = shared,
    excludedFromStats = excludedFromStats
)

/** Request body for one row. user_id and updated_at are the server's to set. */
fun CloudTransaction.toJson(): JSONObject = JSONObject().apply {
    put("id", uuid)
    put("timestamp", timestamp)
    put("amount", amount ?: JSONObject.NULL)
    put("type", type.name)
    put("merchant", merchant ?: JSONObject.NULL)
    put("category", category?.name ?: JSONObject.NULL)
    put("account", account ?: JSONObject.NULL)
    put("source", source.name)
    put("shared", shared)
    put("excluded_from_stats", excludedFromStats)
    put("deleted", deleted)
}

/**
 * A row as the server returns it. Values a newer app version might write
 * (unknown category or type) degrade to "unknown" instead of failing the pull.
 */
fun cloudTransactionFrom(json: JSONObject): CloudTransaction = CloudTransaction(
    uuid = json.getString("id"),
    timestamp = json.getLong("timestamp"),
    amount = json.doubleOrNull("amount"),
    type = runCatching { TxType.valueOf(json.getString("type")) }.getOrDefault(TxType.UNKNOWN),
    merchant = json.stringOrNull("merchant"),
    category = json.stringOrNull("category")?.let { runCatching { Category.valueOf(it) }.getOrNull() },
    account = json.stringOrNull("account"),
    source = runCatching { TxSource.valueOf(json.getString("source")) }.getOrDefault(TxSource.NOTIFICATION),
    shared = json.optBoolean("shared", false),
    excludedFromStats = json.optBoolean("excluded_from_stats", false),
    deleted = json.optBoolean("deleted", false),
    updatedAt = json.stringOrNull("updated_at")
)

private fun JSONObject.stringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)

private fun JSONObject.doubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else getDouble(key)
