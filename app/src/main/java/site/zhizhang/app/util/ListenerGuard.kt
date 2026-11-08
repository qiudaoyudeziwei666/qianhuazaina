package site.zhizhang.app.util

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.util.Log
import site.zhizhang.app.core.ListenerHealth
import site.zhizhang.app.data.SettingsStore
import site.zhizhang.app.service.PaymentNotificationListener

/**
 * 无 Root 前提下能做的「自愈」：请 Android 系统重新绑定通知监听服务。
 *
 * 触发时机：开机、应用升级、用户重新打开 App 且发现服务长时间没有动静、设置页手动点自检。
 * `requestRebind` 是系统官方 API（API 24+），不需要额外权限，也不需要 Root。
 *
 * 明确边界：它只覆盖「授权还在、服务解绑」的情况。如果系统把「通知使用权」
 * 整个撤销（HyperOS 更新、权限重置、用户在设置里关掉），这个方法救不了，
 * 必须用户手动勾选或重新执行一次 ADB 授权脚本。
 * App 也拿不到「永远不被停止」的权利——强行停止、系统冻结、卸载都是系统/用户的合法操作。
 */
object ListenerGuard {
    private const val TAG = "ZhizhangGuard"

    /** 主动请系统重新绑定一次；成功发出请求返回 true，不代表绑定一定成功 */
    fun requestRebind(context: Context): Boolean = runCatching {
        NotificationListenerService.requestRebind(
            ComponentName(context.applicationContext, PaymentNotificationListener::class.java)
        )
        SettingsStore(context).lastRebindAt = System.currentTimeMillis()
        Log.i(TAG, "requestRebind sent")
        true
    }.getOrDefault(false)

    /** 只在「超过 30 分钟没收到任何通知」时才请求重绑，避免频繁重绑造成短暂漏通知 */
    fun ensureIfStale(context: Context): Boolean {
        val last = SettingsStore(context).lastNotificationAt
        if (!ListenerHealth.shouldRebind(last)) return false
        Log.i(TAG, "listener looks stale (last=$last), requesting rebind")
        return requestRebind(context)
    }

    /** 用户在设置页主动点「自检」时强制执行；若权限登记丢了且 App 有写安全设置的能力，先补登记 */
    fun ensureNow(context: Context): Boolean {
        if (!PermissionHelper.isNotificationListenerEnabled(context) &&
            PermissionHelper.hasSecureSettingsPermission(context)
        ) {
            PermissionHelper.enableListenerAfterAdbGrant(context)
        }
        return requestRebind(context)
    }
}
