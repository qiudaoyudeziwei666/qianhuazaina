package site.zhizhang.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            LedgerWidgetProvider.updateAll(context)
            // 开机 / 应用升级后：常驻守护 + 若服务长时间没有动静则主动请系统重新绑定
            site.zhizhang.app.service.GuardForegroundService.start(context)
            site.zhizhang.app.util.ListenerGuard.ensureIfStale(context)
        }
    }
}
