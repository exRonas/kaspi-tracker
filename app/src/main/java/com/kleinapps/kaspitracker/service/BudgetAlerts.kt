package com.kleinapps.kaspitracker.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.kleinapps.kaspitracker.R
import com.kleinapps.kaspitracker.data.Budget
import com.kleinapps.kaspitracker.data.Category
import com.kleinapps.kaspitracker.util.formatMoney

/**
 * Warns once when a category crosses 80% of its monthly budget, and once more
 * when it goes over.
 *
 * "Once" is per category, per month, per level — the state lives in
 * SharedPreferences so a restart doesn't re-fire an alert the user already saw,
 * and the keys reset naturally when the month changes.
 */
object BudgetAlerts {

    private const val CHANNEL_ID = "budget_alerts"
    private const val PREFS = "budget_alerts"
    private const val WARN_AT = 0.8

    /** Result of one check, so callers (and tests) can see what was decided. */
    enum class Level { NONE, WARNING, EXCEEDED }

    fun levelFor(spent: Double, limit: Double): Level = when {
        limit <= 0 -> Level.NONE
        spent >= limit -> Level.EXCEEDED
        spent >= limit * WARN_AT -> Level.WARNING
        else -> Level.NONE
    }

    /**
     * Notifies about [category] if it just crossed a threshold. Safe to call
     * after every insert: repeated calls at the same level do nothing.
     */
    fun notifyIfNeeded(context: Context, budget: Budget, spent: Double, monthKey: String) {
        val level = levelFor(spent, budget.monthlyLimit)
        if (level == Level.NONE) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "$monthKey|${budget.category.name}"
        if (prefs.getString(key, null) == level.name) return

        if (!canPostNotifications(context)) {
            // Remember anyway: the user declined notifications, and re-checking
            // on every insert would just burn cycles.
            prefs.edit().putString(key, level.name).apply()
            return
        }

        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titleFor(level, budget.category))
            .setContentText(bodyFor(spent, budget.monthlyLimit))
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyFor(spent, budget.monthlyLimit)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(budget.category.name.hashCode(), notification)
        }
        prefs.edit().putString(key, level.name).apply()
    }

    private fun titleFor(level: Level, category: Category): String = when (level) {
        Level.EXCEEDED -> "Бюджет превышен: ${category.label}"
        else -> "Бюджет заканчивается: ${category.label}"
    }

    private fun bodyFor(spent: Double, limit: Double): String {
        val left = limit - spent
        return if (left >= 0) {
            "Потрачено ${formatMoney(spent)} из ${formatMoney(limit)}. Осталось ${formatMoney(left)}."
        } else {
            "Потрачено ${formatMoney(spent)} из ${formatMoney(limit)} — перерасход ${formatMoney(-left)}."
        }
    }

    private fun canPostNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Бюджеты",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Предупреждения о превышении месячного лимита" }
        )
    }
}
