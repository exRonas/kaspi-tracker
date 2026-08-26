package com.kleinapps.kaspitracker.receipt

import android.content.Context
import android.net.Uri
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TransactionRepository
import com.kleinapps.kaspitracker.data.TxSource
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ReceiptImportResult {
    data class Success(val amount: Double?, val counterparty: String?) : ReceiptImportResult()

    /** The payment was already recorded from a Kaspi push; the row was enriched. */
    data class Merged(val amount: Double?, val counterparty: String?) : ReceiptImportResult()
    data class Duplicate(val amount: Double?) : ReceiptImportResult()
    data class Failed(val reason: String) : ReceiptImportResult()
}

/** Outcome of importing a whole batch of receipt PDFs at once. */
data class BatchImportResult(
    val added: Int = 0,
    val merged: Int = 0,
    val duplicates: Int = 0,
    val failed: Int = 0,
    val failures: List<String> = emptyList()
) {
    val total: Int get() = added + merged + duplicates + failed

    fun plus(result: ReceiptImportResult): BatchImportResult = when (result) {
        is ReceiptImportResult.Success -> copy(added = added + 1)
        is ReceiptImportResult.Merged -> copy(merged = merged + 1)
        is ReceiptImportResult.Duplicate -> copy(duplicates = duplicates + 1)
        is ReceiptImportResult.Failed -> copy(
            failed = failed + 1,
            // Only the first few reasons are worth showing; the rest are noise.
            failures = if (failures.size < 5) failures + result.reason else failures
        )
    }
}

/**
 * Extracts text from a shared Kaspi receipt PDF (via Android's "Поделиться" share
 * sheet) and stores it as a [Transaction]. Runs fully offline — no network calls.
 *
 * Two-stage extraction: the PDF's own text layer first (cheap), then OCR via
 * [PdfOcr] — Kaspi transfer receipts are image-only and have no text layer at all.
 */
object ReceiptImporter {

    /**
     * Imports a whole selection of receipt PDFs, reporting progress after each
     * one. OCR of a scanned receipt takes seconds, so a batch of thirty is a
     * minute of work — [onProgress] is what keeps the screen honest about it.
     */
    suspend fun importAll(
        context: Context,
        uris: List<Uri>,
        onProgress: (done: Int, total: Int, running: BatchImportResult) -> Unit = { _, _, _ -> }
    ): BatchImportResult {
        var summary = BatchImportResult()
        uris.forEachIndexed { index, uri ->
            val result = runCatching { importFromUri(context, uri) }
                .getOrElse { ReceiptImportResult.Failed(it.message ?: "неизвестная ошибка") }
            summary = summary.plus(result)
            onProgress(index + 1, uris.size, summary)
        }
        return summary
    }

    suspend fun importFromUri(context: Context, uri: Uri): ReceiptImportResult =
        withContext(Dispatchers.IO) {
            val textLayer = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    PDDocument.load(stream).use { document ->
                        PDFTextStripper().getText(document)
                    }
                }
            }.onFailure { it.printStackTrace() }.getOrNull()

            var parsed = textLayer?.takeIf { it.isNotBlank() }?.let { KaspiReceiptParser.parse(it) }

            // No text layer, or it produced no usable amount — OCR the rendered page.
            if (parsed?.amount == null) {
                val ocrText = PdfOcr.extractText(context, uri)
                if (!ocrText.isNullOrBlank()) {
                    val ocrParsed = KaspiReceiptParser.parse(ocrText)
                    // Prefer OCR only when it actually recognized more than the text layer.
                    if (parsed == null || ocrParsed.amount != null) parsed = ocrParsed
                }
            }

            // Full-page OCR often skips the big colored headline amount ("100 ₸"
            // on the green banner) — recover it with a digits-only pass over the
            // banner zone.
            if (parsed != null && parsed?.amount == null) {
                val headline = PdfOcr.extractHeadlineNumber(context, uri)
                if (headline != null) parsed = parsed?.copy(amount = headline)
            }

            val result = parsed
                ?: return@withContext ReceiptImportResult.Failed(
                    "Не удалось прочитать PDF — ни текстом, ни распознаванием."
                )

            val repo = TransactionRepository.get(context)

            // Sharing the same receipt twice must not double the history.
            val timestamp = result.timestamp ?: System.currentTimeMillis()
            if (result.timestamp != null &&
                repo.isReceiptDuplicate(result.timestamp, result.amount)
            ) {
                return@withContext ReceiptImportResult.Duplicate(result.amount)
            }

            // insertReceipt() merges into the Kaspi push for the same payment
            // when there is one, instead of recording the money twice.
            val insertedId = repo.insertReceipt(
                Transaction(
                    timestamp = timestamp,
                    title = "Импортированный чек",
                    rawText = result.rawText,
                    amount = result.amount,
                    type = result.type,
                    merchant = result.counterparty,
                    source = TxSource.RECEIPT,
                    account = result.account
                )
            )
            // A null id means the receipt was folded into the Kaspi push for the
            // same payment instead of becoming a second operation.
            if (insertedId == null) {
                ReceiptImportResult.Merged(result.amount, result.counterparty)
            } else {
                ReceiptImportResult.Success(result.amount, result.counterparty)
            }
        }
}
