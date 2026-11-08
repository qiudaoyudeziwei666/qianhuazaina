package site.zhizhang.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Binder
import site.zhizhang.app.data.SettingsStore

/**
 * 仅用于电脑 ADB 调试：开关「接受模拟通知」。
 * 只接受系统(1000)、shell(2000) 或 root(0) 发来的广播，第三方 App 无法调用。
 * 生产使用时应保持关闭。
 */
class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 广播由 system_server 分发时这里通常是 uid=1000，adb shell 直发可能是 2000；
        // 之前只放行 2000/0，导致 ADB 广播静默失效。第三方 App 的 uid 仍会被拒绝。
        val uid = Binder.getCallingUid()
        if (uid != 1000 && uid != 2000 && uid != 0) return
        when (intent.action) {
            ACTION_SET_DEBUG -> SettingsStore(context).acceptDebugNotifications =
                intent.getBooleanExtra(EXTRA_ENABLED, false)
            // 仅供电脑验证自愈链路，正式发布包里不生效
            ACTION_REBIND -> if (site.zhizhang.app.BuildConfig.DEBUG) {
                site.zhizhang.app.util.ListenerGuard.requestRebind(context)
            }
        }
    }

    companion object {
        const val ACTION_SET_DEBUG = "site.zhizhang.app.SET_DEBUG_NOTIFICATIONS"
        const val EXTRA_ENABLED = "enabled"
        const val ACTION_REBIND = "site.zhizhang.app.REBIND_LISTENER"
    }
}
