package com.kleinapps.kaspitracker.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.kleinapps.kaspitracker.data.TransactionRepository
import com.kleinapps.kaspitracker.util.isNotificationAccessGranted
import java.util.concurrent.TimeUnit

/**
 * Keeps the notification listener alive, and tells the user when it isn't.
 *
 * Losing the listener is the app's worst failure mode because it is invisible:
 * spending simply stops being recorded while every screen still looks fine. The
 * watchdog wakes up periodically, and if Kaspi has been quiet for longer than
 * the user's own rhythm explains, it tries to bring the binding back and raises
 * a banner on the home screen.
 */
object ListenerWatchdog {

    private const val TAG = "ListenerWatchdog"
    private const val WORK_NAME = "listener_watchdog"
    private const val PREFS = "listener_health"
    private const val KEY_LAST_SEEN = "last_notification_at"

    /** Registers the periodic check. Existing schedules are left alone. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WatchdogWorker>(6, TimeUnit.HOURS)
            .setInitialDelay(6, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Called from the listener on every Kaspi push, parsed or not. */
    fun recordNotificationSeen(context: Context, at: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_LAST_SEEN, at)
            .apply()
    }

    /** When the last Kaspi push arrived, or null if none has been seen yet. */
    fun lastNotificationAt(context: Context): Long? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SEEN, 0L)
            .takeIf { it > 0 }

    /**
     * Asks Android to re-bind the listener.
     *
     * `requestRebind` alone is frequently ignored on Samsung once the process
     * has been frozen, so the component is also toggled off and on: that forces
     * the system to tear the binding down and build a new one. `DONT_KILL_APP`
     * keeps the rest of the app running while it happens.
     */
    fun forceRebind(context: Context) {
        val component = ComponentName(context, KaspiNotificationListener::class.java)

        runCatching { NotificationListenerService.requestRebind(component) }
            .onFailure { Log.w(TAG, "requestRebind failed", it) }

        runCatching {
            val packageManager = context.packageManager
            packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            packageManager.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        }.onFailure { Log.w(TAG, "component toggle failed", it) }
    }
}

/**
 * Periodic health check. Reports success either way — a quiet listener is a
 * finding, not a failed job.
 */
class WatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        // Nothing to revive if the user never granted access in the first place;
        // the home screen already asks for it in that case.
        if (!isNotificationAccessGranted(context)) return Result.success()

        val lastSeen = ListenerWatchdog.lastNotificationAt(context) ?: return Result.success()
        val history = runCatching {
            TransactionRepository.get(context).notificationTimestamps()
        }.getOrDefault(emptyList())

        if (NotificationHealth.isSilent(lastSeen, history)) {
            Log.w(
                "ListenerWatchdog",
                "No Kaspi push for ${NotificationHealth.hoursSince(lastSeen)}h — rebinding"
            )
            ListenerWatchdog.forceRebind(context)
        }
        return Result.success()
    }
}
