package com.kleinapps.kaspitracker.util

import android.content.Context
import android.provider.Settings

/**
 * Whether this app currently holds notification-listener access.
 *
 * We read the system secure setting directly (rather than relying on the
 * service lifecycle) so the Home banner reflects reality even before the
 * service binds.
 */
fun isNotificationAccessGranted(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    val pkg = context.packageName
    return enabled.split(':')
        .mapNotNull { it.takeIf(String::isNotBlank) }
        .any { component -> component.contains(pkg) }
}
