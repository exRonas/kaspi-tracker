package com.kleinapps.kaspitracker.receipt

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File

/**
 * OCR pipeline for image-only Kaspi receipt PDFs.
 *
 * Kaspi transfer receipts ("Поделиться чеком" after sending money) contain no
 * text layer at all — the whole receipt is a rendered image. This renders the
 * first page with [PdfRenderer] at high resolution and runs offline Tesseract
 * (rus+eng, traineddata shipped in assets/tessdata) over the bitmap.
 */
object PdfOcr {

    private const val TAG = "PdfOcr"
    private const val LANGS = "rus+eng"

    /** Render scale relative to the PDF's natural page size. Higher = better OCR, slower. */
    private const val RENDER_SCALE = 3

    /** Returns recognized text, or null when rendering/recognition failed. */
    fun extractText(context: Context, uri: Uri): String? {
        val bitmap = renderFirstPage(context, uri) ?: return null
        return try {
            recognize(context, bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Second-chance pass for the headline amount ("100 ₸" on the green banner):
     * large colored text on a tinted background that full-page recognition often
     * skips. Crops the banner zone (top ~40% of the page) and re-runs Tesseract
     * restricted to digit-like characters, returning the biggest number found.
     */
    fun extractHeadlineNumber(context: Context, uri: Uri): Double? {
        val page = renderFirstPage(context, uri) ?: return null
        return try {
            val crop = Bitmap.createBitmap(page, 0, 0, page.width, (page.height * 0.4f).toInt())
            try {
                val text = recognize(context, crop) {
                    it.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789 .,₸")
                }
                text?.lines()
                    ?.mapNotNull { line ->
                        line.filter { ch -> ch.isDigit() || ch == ' ' || ch == '.' || ch == ',' }
                            .trim()
                            .takeIf { it.any(Char::isDigit) }
                            ?.let(::normalizeDigits)
                    }
                    ?.maxOrNull()
            } finally {
                if (crop !== page) crop.recycle()
            }
        } finally {
            page.recycle()
        }
    }

    private fun normalizeDigits(raw: String): Double? {
        val cleaned = raw.replace(" ", "").replace(',', '.')
        return cleaned.toDoubleOrNull()
    }

    private fun renderFirstPage(context: Context, uri: Uri): Bitmap? = runCatching {
        // PdfRenderer needs a seekable fd; content providers may hand out pipes,
        // so the stream is always copied to a temp file first.
        val tmp = File.createTempFile("receipt", ".pdf", context.cacheDir)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null

            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return@runCatching null
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(
                            page.width * RENDER_SCALE,
                            page.height * RENDER_SCALE,
                            Bitmap.Config.ARGB_8888
                        )
                        // White background — PDF pages are transparent by default
                        // and Tesseract needs dark-on-light input.
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } finally {
            tmp.delete()
        }
    }.onFailure { Log.e(TAG, "PDF render failed", it) }.getOrNull()

    private fun recognize(
        context: Context,
        bitmap: Bitmap,
        configure: (TessBaseAPI) -> Unit = {}
    ): String? = runCatching {
        val dataDir = ensureTessData(context) ?: return@runCatching null
        val api = TessBaseAPI()
        try {
            if (!api.init(dataDir.absolutePath, LANGS)) return@runCatching null
            // Receipts are a single column of text with mixed font sizes.
            api.pageSegMode = TessBaseAPI.PageSegMode.PSM_SINGLE_COLUMN
            configure(api)
            api.setImage(bitmap)
            api.utF8Text?.takeIf { it.isNotBlank() }
        } finally {
            api.recycle()
        }
    }.onFailure { Log.e(TAG, "OCR failed", it) }.getOrNull()

    /**
     * Copies bundled traineddata from assets to filesDir on first use.
     * Tesseract expects `<dataDir>/tessdata/<lang>.traineddata`.
     */
    private fun ensureTessData(context: Context): File? = runCatching {
        val dataDir = File(context.filesDir, "ocr")
        val tessDir = File(dataDir, "tessdata").apply { mkdirs() }
        LANGS.split("+").forEach { lang ->
            val target = File(tessDir, "$lang.traineddata")
            if (!target.exists() || target.length() == 0L) {
                context.assets.open("tessdata/$lang.traineddata").use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
            }
        }
        dataDir
    }.onFailure { Log.e(TAG, "tessdata copy failed", it) }.getOrNull()
}
