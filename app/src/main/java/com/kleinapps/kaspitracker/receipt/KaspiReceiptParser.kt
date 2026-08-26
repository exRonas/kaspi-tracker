package com.kleinapps.kaspitracker.receipt

import com.kleinapps.kaspitracker.data.TxType
import com.kleinapps.kaspitracker.parser.KaspiNotificationParser
import java.text.SimpleDateFormat
import java.util.Locale

/** Result of parsing the text of a Kaspi PDF receipt ("Поделиться чеком"). */
data class ParsedReceipt(
    val amount: Double?,
    val type: TxType,
    val counterparty: String?,
    val timestamp: Long?,
    /** Source account, e.g. "Kaspi Gold" — from the receipt's "Откуда" row. */
    val account: String?,
    val rawText: String
)

/**
 * Parses text extracted from a Kaspi "Фискальный чек" / payment receipt PDF —
 * either a real text layer (PDFBox) or OCR output (Tesseract, see [PdfOcr]).
 *
 * Kaspi generates these receipts as a label/value table (e.g. "Дата и время" /
 * "25.06.2026 16:00") rendered in roughly visual reading order. Everything here
 * is a best-effort heuristic (same philosophy as [KaspiNotificationParser]), and
 * must tolerate OCR noise: the ₸ sign is often lost or misread, so amounts are
 * also accepted as bare numbers in known positions.
 */
object KaspiReceiptParser {

    private val DATE_TIME_REGEX = Regex("""(\d{2}\.\d{2}\.\d{4})\s+(\d{2}:\d{2})""")
    private val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    // A line that is (almost) nothing but a number — the headline amount as OCR
    // sees it when the ₸ glyph is dropped or misread into a stray letter
    // (case-insensitive: "703 T", "703 т" and "703 f" all occur).
    private val BARE_AMOUNT_REGEX =
        Regex("""^\s*([\d\s .,]*\d)\s*[₸TТF]?\s*$""", RegexOption.IGNORE_CASE)

    // Headings that introduce the headline amount in different receipt kinds:
    // payment receipts ("Оплата совершена") and transfer receipts ("Перевод
    // успешно совершен", e.g. transfer-receipt-*.pdf shared after sending money).
    private val RECEIPT_HEADINGS = listOf(
        "оплата совершена", "оплата успешна", "оплата успешно совершена",
        "платёж совершен", "платеж совершен",
        // Service payments (public transport, utilities) word it this way.
        "платёж успешно совершен", "платеж успешно совершен",
        "перевод успешно совершен", "перевод совершен", "покупка совершена"
    )

    private val ACCOUNT_REGEX = Regex(
        """откуда\s*:?\s*(.+)""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String): ParsedReceipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val amount = extractHeadlineAmount(lines) ?: KaspiNotificationParser.extractAmount(text)
        val type = KaspiNotificationParser.classifyType(text)
        val counterparty = extractCounterparty(lines)
        val timestamp = extractTimestamp(text)
        val account = extractAccount(lines)

        return ParsedReceipt(
            amount = amount,
            type = type,
            counterparty = counterparty,
            timestamp = timestamp,
            account = account,
            rawText = lines.joinToString(separator = "\n")
        )
    }

    /**
     * The headline amount follows the "Оплата совершена"-style heading — either
     * on the same line or the next one (e.g. "100 ₸"). Not the per-item
     * breakdown further down the receipt.
     */
    private fun extractHeadlineAmount(lines: List<String>): Double? {
        val headingIndex = lines.indexOfFirst { line ->
            RECEIPT_HEADINGS.any { line.lowercase().contains(it) }
        }
        if (headingIndex == -1) return null

        // Same line first (OCR sometimes merges heading + amount), then the next few.
        (headingIndex..minOf(headingIndex + 2, lines.lastIndex)).forEach { i ->
            val candidate =
                if (i == headingIndex) {
                    // Strip the heading words, keep the tail for amount matching.
                    RECEIPT_HEADINGS.fold(lines[i].lowercase()) { acc, h -> acc.replace(h, "") }
                } else {
                    lines[i]
                }
            KaspiNotificationParser.extractAmount(candidate)?.let { return it }
            BARE_AMOUNT_REGEX.find(candidate)?.let { match ->
                KaspiNotificationParser.normalizeAmount(match.groupValues[1])?.let { return it }
            }
        }
        return null
    }

    /**
     * The merchant/recipient name sits on the line directly below the headline
     * amount (e.g. "ИП Старина", "Дмитрий С."). Best-effort — falls back to null.
     *
     * Service receipts (public transport, utilities) instead print the service
     * name *above* the "Платеж успешно совершен" heading and put a per-payment
     * receipt number right below the amount — taking that number as the merchant
     * produced a brand-new "merchant" on every single ride, so the header wins
     * when there is one.
     */
    private fun extractCounterparty(lines: List<String>): String? {
        headerName(lines)?.let { return it }

        val amountLineIndex = lines.indexOfFirst { line ->
            KaspiNotificationParser.extractAmount(line) != null || BARE_AMOUNT_REGEX.matches(line)
        }
        // The name sits right below the amount; when OCR lost the amount line
        // entirely, it sits right below the receipt heading instead.
        val anchorIndex =
            if (amountLineIndex != -1) {
                amountLineIndex
            } else {
                lines.indexOfFirst { line ->
                    RECEIPT_HEADINGS.any { line.lowercase().contains(it) }
                }
            }
        if (anchorIndex == -1) return null
        // The first usable line below the anchor — the line straight after the
        // amount can be a receipt/document number ("№ квитанции QR16793561441").
        return lines.drop(anchorIndex + 1).firstOrNull { isName(it) }?.take(60)
    }

    /**
     * The service name printed above the payment heading, e.g. "Общественный
     * транспорт" on an Avtobys ticket. Generic document titles ("Фискальный
     * чек") don't count.
     */
    private fun headerName(lines: List<String>): String? {
        val headingIndex = lines.indexOfFirst { line ->
            RECEIPT_HEADINGS.any { line.lowercase().contains(it) }
        }
        if (headingIndex <= 0) return null
        return lines.take(headingIndex).firstOrNull { isName(it) }?.take(60)
    }

    /** Labels and numbers that are never a merchant name. */
    private val LABEL_PREFIXES = listOf(
        "№", "no ", "идентификатор", "госномер", "дата", "время", "фио", "оплачено с",
        "адрес", "иин", "бин", "рнм", "знм", "фп ", "офд", "тип перевода", "комиссия",
        "отправитель", "получатель", "откуда", "куда", "чек не является", "фискальный чек",
        "продажа", "квитанц", "сумма", "статус", "назначение", "детали"
    )

    private fun isName(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length !in 2..60) return false
        if (KaspiNotificationParser.extractAmount(trimmed) != null) return false
        if (BARE_AMOUNT_REGEX.matches(trimmed)) return false
        val lower = trimmed.lowercase()
        if (LABEL_PREFIXES.any { lower.startsWith(it) }) return false
        // Plate numbers / ids like "904AI14" — no lowercase word, digits inside.
        if (trimmed.none { it.isLetter() }) return false
        if (trimmed.any { it.isDigit() } && trimmed.none { it.isWhitespace() } &&
            trimmed.count { it.isDigit() } >= trimmed.count { it.isLetter() }
        ) return false
        return true
    }

    /** "Откуда  Kaspi Gold" — value on the same line or the next one. */
    private fun extractAccount(lines: List<String>): String? {
        lines.forEachIndexed { i, line ->
            val match = ACCOUNT_REGEX.find(line) ?: return@forEachIndexed
            val sameLine = match.groupValues[1].trim()
            if (sameLine.isNotBlank()) return sameLine.take(40)
            if (i + 1 < lines.size) return lines[i + 1].trim().take(40)
        }
        return null
    }

    private fun extractTimestamp(text: String): Long? {
        val match = DATE_TIME_REGEX.find(text) ?: return null
        return runCatching { dateTimeFormat.parse(match.value)?.time }.getOrNull()
    }
}
