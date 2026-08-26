package com.kleinapps.kaspitracker.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Everything the app knows, as plain JSON — operations, budgets and the learned
 * category rules.
 *
 * The format is deliberately boring (a flat object per row) so a backup made by
 * one version still restores into the next one: unknown fields are ignored and
 * missing fields fall back to the entity defaults.
 */
object Backup {

    const val VERSION = 1
    const val FILE_PREFIX = "kaspi_tracker_backup"

    data class Content(
        val transactions: List<Transaction>,
        val budgets: List<Budget>,
        val rules: List<CategoryRule>
    )

    data class RestoreReport(
        val added: Int,
        val skipped: Int,
        val budgets: Int,
        val rules: Int
    )

    fun encode(content: Content): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put(
            "transactions",
            JSONArray().apply {
                content.transactions.forEach { put(encodeTransaction(it)) }
            }
        )
        root.put(
            "budgets",
            JSONArray().apply {
                content.budgets.forEach {
                    put(
                        JSONObject()
                            .put("category", it.category.name)
                            .put("monthlyLimit", it.monthlyLimit)
                    )
                }
            }
        )
        root.put(
            "rules",
            JSONArray().apply {
                content.rules.forEach {
                    put(
                        JSONObject()
                            .put("merchantKey", it.merchantKey)
                            .put("category", it.category.name)
                            .put("updatedAt", it.updatedAt)
                    )
                }
            }
        )
        return root.toString(2)
    }

    fun decode(json: String): Content {
        val root = JSONObject(json)
        return Content(
            transactions = root.optJSONArray("transactions").map { decodeTransaction(it) },
            budgets = root.optJSONArray("budgets").mapNotNull { obj ->
                val category = obj.optString("category").toCategoryOrNull() ?: return@mapNotNull null
                Budget(category, obj.optDouble("monthlyLimit", 0.0))
            },
            rules = root.optJSONArray("rules").mapNotNull { obj ->
                val key = obj.optString("merchantKey").takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val category = obj.optString("category").toCategoryOrNull() ?: return@mapNotNull null
                CategoryRule(key, category, obj.optLong("updatedAt", System.currentTimeMillis()))
            }
        )
    }

    private fun encodeTransaction(tx: Transaction): JSONObject = JSONObject().apply {
        put("timestamp", tx.timestamp)
        put("title", tx.title ?: JSONObject.NULL)
        put("rawText", tx.rawText)
        put("amount", tx.amount ?: JSONObject.NULL)
        put("type", tx.type.name)
        put("merchant", tx.merchant ?: JSONObject.NULL)
        put("currency", tx.currency)
        put("source", tx.source.name)
        put("category", tx.category?.name ?: JSONObject.NULL)
        put("account", tx.account ?: JSONObject.NULL)
        put("dedupKey", tx.dedupKey ?: JSONObject.NULL)
        put("balance", tx.balance ?: JSONObject.NULL)
    }

    private fun decodeTransaction(obj: JSONObject) = Transaction(
        timestamp = obj.optLong("timestamp"),
        title = obj.optStringOrNull("title"),
        rawText = obj.optString("rawText"),
        amount = obj.optDoubleOrNull("amount"),
        type = runCatching { TxType.valueOf(obj.optString("type")) }.getOrDefault(TxType.UNKNOWN),
        merchant = obj.optStringOrNull("merchant"),
        currency = obj.optString("currency").takeIf { it.isNotBlank() } ?: "₸",
        source = runCatching { TxSource.valueOf(obj.optString("source")) }
            .getOrDefault(TxSource.NOTIFICATION),
        category = obj.optString("category").toCategoryOrNull(),
        account = obj.optStringOrNull("account"),
        dedupKey = obj.optStringOrNull("dedupKey"),
        balance = obj.optDoubleOrNull("balance")
    )

    /**
     * Identity of an operation across devices: the row id is local, so restoring
     * matches on what actually describes the payment.
     */
    fun identityOf(tx: Transaction): String =
        "${tx.timestamp}|${tx.amount ?: ""}|${tx.source}|${tx.rawText.take(120)}"

    private fun String?.toCategoryOrNull(): Category? =
        this?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { runCatching { Category.valueOf(it) }.getOrNull() }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private inline fun <T> JSONArray?.mapNotNull(transform: (JSONObject) -> T?): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(transform) }
    }

    private inline fun <T> JSONArray?.map(transform: (JSONObject) -> T): List<T> =
        mapNotNull { transform(it) }
}
