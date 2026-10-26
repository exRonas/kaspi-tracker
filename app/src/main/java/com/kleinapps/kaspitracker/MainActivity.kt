package com.kleinapps.kaspitracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kleinapps.kaspitracker.receipt.ReceiptImportResult
import com.kleinapps.kaspitracker.sync.CoupleStore
import com.kleinapps.kaspitracker.sync.SyncScheduler
import com.kleinapps.kaspitracker.receipt.ReceiptImporter
import com.kleinapps.kaspitracker.ui.AppNavigation
import com.kleinapps.kaspitracker.ui.theme.KaspiTrackerTheme
import com.kleinapps.kaspitracker.util.formatMoney
import com.kleinapps.kaspitracker.util.isNotificationAccessGranted

class MainActivity : ComponentActivity() {

    private var pendingReceiptUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingReceiptUris = extractReceiptUris(intent)

        setContent {
            KaspiTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val lifecycleOwner = LocalLifecycleOwner.current
                    // Re-check the permission each time we return to the foreground,
                    // since the user grants it in system settings (another screen).
                    var granted by remember { mutableStateOf(isNotificationAccessGranted(context)) }
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                granted = isNotificationAccessGranted(context)
                                // Opening the app is when fresh partner data matters most.
                                if (CoupleStore.state.value.paired) SyncScheduler.requestSoon(context)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    // Triggered when Kaspi receipt PDFs are shared into the app
                    // (cold start via onCreate, or while running via onNewIntent).
                    // A single share and a multi-select share take the same path.
                    LaunchedEffect(pendingReceiptUris) {
                        val uris = pendingReceiptUris
                        if (uris.isEmpty()) return@LaunchedEffect
                        // Don't clear the list yet — doing so here would change this
                        // very effect's key and Compose would cancel this coroutine
                        // mid-import ("The coroutine scope left the composition").
                        Toast.makeText(
                            context,
                            if (uris.size == 1) "Импортирую чек…" else "Импортирую ${uris.size} чеков…",
                            Toast.LENGTH_SHORT
                        ).show()
                        val message = try {
                            if (uris.size == 1) {
                                describe(ReceiptImporter.importFromUri(context, uris.single()))
                            } else {
                                val summary = ReceiptImporter.importAll(context, uris)
                                buildString {
                                    append("Готово: добавлено ${summary.added}")
                                    if (summary.merged > 0) append(", дополнено ${summary.merged}")
                                    if (summary.duplicates > 0) append(", уже было ${summary.duplicates}")
                                    if (summary.failed > 0) append(", не прочиталось ${summary.failed}")
                                }
                            }
                        } catch (e: Exception) {
                            "Ошибка импорта чека: ${e.message}"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        pendingReceiptUris = emptyList()
                    }

                    AppNavigation(notificationAccessGranted = granted)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractReceiptUris(intent).takeIf { it.isNotEmpty() }?.let { pendingReceiptUris = it }
    }

    private fun describe(result: ReceiptImportResult): String = when (result) {
        is ReceiptImportResult.Success -> buildString {
            append("Чек добавлен: ")
            append(formatMoney(result.amount))
            result.counterparty?.let { append(" · $it") }
        }
        is ReceiptImportResult.Merged -> buildString {
            append("Чек дополнил уже записанную операцию: ")
            append(formatMoney(result.amount))
            result.counterparty?.let { append(" · $it") }
        }
        is ReceiptImportResult.Duplicate ->
            "Этот чек уже импортирован (${formatMoney(result.amount)})"
        is ReceiptImportResult.Failed -> result.reason
    }

    /**
     * Streams attached to a share intent, single or multi-select.
     *
     * Deliberately not gated on the declared MIME type — some senders tag the
     * same PDF bytes as a generic octet-stream. A non-PDF simply fails to parse
     * later (reported via Toast) instead of being silently ignored here.
     */
    private fun extractReceiptUris(intent: Intent?): List<Uri> = when (intent?.action) {
        Intent.ACTION_SEND ->
            listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            )
        Intent.ACTION_SEND_MULTIPLE ->
            IntentCompat.getParcelableArrayListExtra(
                intent, Intent.EXTRA_STREAM, Uri::class.java
            ).orEmpty()
        else -> emptyList()
    }
}
