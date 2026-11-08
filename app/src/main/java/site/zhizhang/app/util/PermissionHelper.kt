package site.zhizhang.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import site.zhizhang.app.service.PaymentNotificationListener

object PermissionHelper {
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        val component = ComponentName(context, PaymentNotificationListener::class.java)
        return enabled.split(':').any {
            it.equals(component.flattenToString(), true) || it.equals(component.flattenToShortString(), true)
        }
    }

    fun openNotificationSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun hasSecureSettingsPermission(context: Context): Boolean =
        context.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun enableListenerAfterAdbGrant(context: Context): Boolean {
        if (!hasSecureSettingsPermission(context)) return false
        return runCatching {
            val component = ComponentName(context, PaymentNotificationListener::class.java).flattenToString()
            val current = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            val components = current.split(':').filter { it.isNotBlank() }.toMutableSet()
            components.add(component)
            Settings.Secure.putString(context.contentResolver, "enabled_notification_listeners", components.joinToString(":"))
            Settings.Secure.putInt(context.contentResolver, "notification_listener_access_enabled", 1)
            true
        }.getOrDefault(false)
    }
}
