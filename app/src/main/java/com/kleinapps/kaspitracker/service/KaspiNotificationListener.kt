package com.kleinapps.kaspitracker.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.kleinapps.kaspitracker.data.Transaction
import com.kleinapps.kaspitracker.data.TransactionRepository
import com.kleinapps.kaspitracker.parser.KaspiNotificationParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Listens to system notifications and persists every Kaspi.kz push as a
 * [Transaction]. Even unparseable notifications are stored (with raw text) so
 * they surface in the Raw Log for parser tuning.
 */
class KaspiNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: TransactionRepository by lazy { TransactionRepository.get(this) }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName != KASPI_PACKAGE) return

        val extras = notification.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        // InboxStyle notifications (used by some grouped/transfer pushes) put their
        // content in a line array instead of EXTRA_TEXT/EXTRA_BIG_TEXT.
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.joinToString(separator = " ") { it.toString() }
        // MessagingStyle (chat-like "X sent you money" pushes) stores content as
        // structured messages, not EXTRA_TEXT — read the last message's text.
        val messagingText = runCatching {
            androidx.core.app.NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification.notification)
                ?.messages
                ?.lastOrNull()
                ?.text
                ?.toString()
        }.getOrNull()

        // Prefer the expanded/structured variants — they usually carry the full message.
        val body = bigText?.takeIf { it.isNotBlank() }
            ?: textLines?.takeIf { it.isNotBlank() }
            ?: messagingText?.takeIf { it.isNotBlank() }
            ?: text
            ?: subText
        val raw = listOfNotNull(title, body).joinToString(separator = " — ").trim()
        if (raw.isBlank()) return

        val parsed = KaspiNotificationParser.parse(title, body)
        val transaction = Transaction(
            timestamp = notification.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
            title = title,
            rawText = raw,
            amount = parsed.amount,
            type = parsed.type,
            merchant = parsed.merchant,
            currency = parsed.currency,
            account = parsed.account,
            dedupKey = KaspiNotificationParser.dedupKey(body, parsed.amount),
            balance = parsed.balance
        )

        scope.launch {
            // insertNotification() drops the second copy of a push: Kaspi posts
            // the group summary and the notification itself milliseconds apart,
            // which used to be recorded as two separate operations.
            runCatching { repository.insertNotification(transaction) }
                .onFailure { Log.e(TAG, "Failed to persist notification", it) }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KaspiListener"
        const val KASPI_PACKAGE = "kz.kaspi.mobile"
    }
}
