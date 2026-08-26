package com.kleinapps.kaspitracker.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.util.Log

/**
 * Re-binds the [KaspiNotificationListener] after a reboot. Some OEM ROMs
 * (Xiaomi/MIUI, Huawei/EMUI) drop notification listeners after a restart, so we
 * explicitly request a rebind once BOOT_COMPLETED fires.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                val component = ComponentName(context, KaspiNotificationListener::class.java)
                runCatching {
                    NotificationListenerService.requestRebind(component)
                }.onFailure { Log.e("BootReceiver", "requestRebind failed", it) }
            }
        }
    }
}
