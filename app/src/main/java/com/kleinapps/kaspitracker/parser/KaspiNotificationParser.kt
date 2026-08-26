package com.kleinapps.kaspitracker.parser

import com.kleinapps.kaspitracker.data.TxType

/**
 * Result of parsing a single Kaspi notification.
 *
 * [amount] is null when no monetary value could be extracted — the caller still
 * persists the raw text so it shows up in the Raw Log for later tuning.
 */
data class ParsedNotification(
    val amount: Double?,
    val type: TxType,
    val merchant: String?,
    val currency: String = "₸", // ₸
    /** Card the operation went through, from the push title ("Kaspi Gold"). */
    val account: String? = null,
    /** Money left on the card, from the "Доступно: … ₸" line. */
    val balance: Double? = null
)

/**
 * Parser for Kaspi.kz push notifications.
 *
 * IMPORTANT: Kaspi does not publish the exact wording of its purchase pushes, so
 * everything here is intentionally heuristic and easy to edit. If real-world
 * notifications don't parse, open the Raw Log screen, copy the actual text, and
 * adjust the regexes / keyword lists below.
 */
object KaspiNotificationParser {

    // Whitespace chars that may be used as thousands separators:
    // plain space, non-breaking (U+00A0), narrow no-break (U+202F), thin (U+2009).
    private const val SPACES = "    "

    /**
     * Matches a money amount immediately followed by a currency marker.
     *
     * The number group `[\d, spaces, ., ,]* \d` captures values like
     * `12 500`, `12 500,75`, `1.234.567`, `990`. The currency marker
     * (case-insensitive: ₸, тг, тенге, kzt) anchors the end of the match.
     */
    private val AMOUNT_REGEX = Regex(
        pattern = "([\\d$SPACES.,]*\\d)\\s*(₸|тг\\.?|тенге|kzt)",
        option = RegexOption.IGNORE_CASE
    )

    // Keyword buckets for classifying the operation type. Edit freely.
    private val PURCHASE_WORDS = listOf("покуп", "оплат", "списан", "товар", "магазин")
    private val TRANSFER_OUT_WORDS = listOf("перевод", "отправлен", "перевели вы", "исходящ")
    private val TRANSFER_IN_WORDS = listOf("получен", "пополнен", "зачислен", "входящ", "поступлен")

    // Heuristic merchant extraction: word(s) after "в"/"у" starting with a capital
    // letter. e.g. "Покупка в Magnum" -> "Magnum", "Оплата в Small ..." -> "Small".
    // NB: a leading `\b` would be useless here — Java's `\b` is defined by the
    // ASCII `\w`, so it never fires in front of the Cyrillic "в"/"у". The
    // lookbehind below is the Unicode-aware equivalent.
    private val MERCHANT_REGEX = Regex(
        "(?<![\\p{L}\\p{N}])[ву]\\s+([A-ZА-ЯЁ][\\p{L}\\p{N}.&'-]*(?:\\s+[A-ZА-ЯЁ0-9][\\p{L}\\p{N}.&'-]*)*)"
    )

    // Cards a push can come from — used to tell "Kaspi Gold" (an account) apart
    // from "Kaspi.kz" / "Магазин на Kaspi.kz" (marketing pushes).
    private val ACCOUNT_TITLE_REGEX = Regex(
        "^Kaspi\\s+(Gold|Red|Blue|Депозит|Deposit|Кредит|Credit)$",
        RegexOption.IGNORE_CASE
    )

    // The "Доступно: 16 643 ₸" tail line — a balance, never the merchant.
    private val BALANCE_LINE_REGEX = Regex(
        "^\\s*(доступно|қолжетімді|available)\\s*:",
        RegexOption.IGNORE_CASE
    )

    fun parse(title: String?, text: String?): ParsedNotification {
        val haystack = listOfNotNull(title, text).joinToString(separator = " ").trim()
        val lower = haystack.lowercase()

        return ParsedNotification(
            amount = extractAmount(haystack),
            type = classifyType(lower),
            merchant = extractCounterparty(text) ?: extractMerchant(haystack),
            account = title?.takeIf { ACCOUNT_TITLE_REGEX.matches(it.trim()) }?.trim(),
            balance = extractBalance(text)
        )
    }

    /** Money left on the card, from the "Доступно: 16 643 ₸" tail line. */
    fun extractBalance(body: String?): Double? =
        body?.lines()
            ?.firstOrNull { BALANCE_LINE_REGEX.containsMatchIn(it.trim()) }
            ?.let { extractAmount(it) }

    /**
     * Kaspi pushes are three lines with a fixed shape:
     *
     * ```
     * Покупка: 1 580 ₸        <- operation and amount
     * GOOGLE *YouTubePremium  <- who got the money (or who sent it)
     * Доступно: 16 643 ₸      <- balance left, optional
     * ```
     *
     * so the counterparty is simply the line below the amount. A trailing
     * comment ("Татьяна Ц.: за Алису") is cut off, otherwise the same person
     * would look like a different merchant on every transfer.
     */
    fun extractCounterparty(body: String?): String? {
        val lines = body?.lines()?.map { it.trim() }?.filter { it.isNotBlank() } ?: return null
        val amountIndex = lines.indexOfFirst { extractAmount(it) != null }
        if (amountIndex == -1) return null

        return lines.drop(amountIndex + 1)
            .firstOrNull { line ->
                !BALANCE_LINE_REGEX.containsMatchIn(line) && extractAmount(line) == null
            }
            ?.substringBefore(':')
            ?.trim()
            ?.take(60)
            ?.takeIf { it.length >= 2 }
    }

    /** Returns the first money amount found, normalized to a Double, or null. */
    fun extractAmount(text: String): Double? {
        val match = AMOUNT_REGEX.find(text) ?: return null
        return normalizeAmount(match.groupValues[1])
    }

    /**
     * Normalizes a raw money string into a Double.
     *  - strips spaces used as thousands separators (incl. non-breaking variants)
     *  - if both "." and "," are present, the rightmost is the decimal separator
     *  - a lone "," is treated as a decimal separator
     *  - a lone "." with exactly 3 trailing digits is treated as grouping, else decimal
     */
    fun normalizeAmount(raw: String): Double? {
        var s = raw.filterNot { it in SPACES }.trim()
        if (s.isEmpty()) return null

        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')

        s = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) s.replace(".", "").replace(',', '.')
                else s.replace(",", "")
            lastComma >= 0 -> s.replace(',', '.')          // lone comma -> decimal
            lastDot >= 0 -> {
                val decimals = s.length - lastDot - 1
                if (decimals == 3) s.replace(".", "") else s // ".###" looks like grouping
            }
            else -> s
        }
        return s.toDoubleOrNull()
    }

    /** Keyword-based classification, shared with [com.kleinapps.kaspitracker.receipt.KaspiReceiptParser]. */
    fun classifyType(text: String): TxType {
        val lower = text.lowercase()
        return when {
            TRANSFER_IN_WORDS.any { lower.contains(it) } -> TxType.TRANSFER_IN
            TRANSFER_OUT_WORDS.any { lower.contains(it) } -> TxType.TRANSFER_OUT
            PURCHASE_WORDS.any { lower.contains(it) } -> TxType.PURCHASE
            else -> TxType.UNKNOWN
        }
    }

    /**
     * Fingerprint of a push, used to recognize the copy that arrives right after
     * the original. Kaspi posts the group summary and the notification itself as
     * two separate pushes whose titles differ ("Платежи" / none) but whose body
     * is identical, so the title is deliberately left out of the key. Punctuation
     * and spacing are normalized because the two copies are not always formatted
     * the same way.
     */
    fun dedupKey(body: String?, amount: Double?): String? {
        val normalized = body
            ?.lowercase()
            ?.replace('ё', 'е')
            ?.replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return "$normalized|${amount ?: ""}"
    }

    private fun extractMerchant(text: String): String? {
        val match = MERCHANT_REGEX.find(text) ?: return null
        return match.groupValues[1]
            .trim()
            .removeSuffix(".")
            .takeIf { it.isNotBlank() && !it.equals("сумму", ignoreCase = true) }
    }
}
