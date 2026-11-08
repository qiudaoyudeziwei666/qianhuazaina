package site.zhizhang.app.core

/**
 * 监听服务的「健康」判断。
 *
 * lastNotificationAt 是服务收到任意一条系统通知的时间，不是「记了一笔账」的时间。
 * 手机上通知很频繁，所以超过 30 分钟没更新，基本可以判定服务已断开或被系统回收，
 * 此时值得主动请系统重新绑定。
 *
 * 注意边界：这只覆盖「授权还在、服务解绑」。如果系统把「通知使用权」整个撤销
 * （HyperOS 更新、权限重置、用户在设置里关掉），requestRebind 救不了，
 * 必须用户手动勾选或重新执行 ADB 授权脚本。
 */
object ListenerHealth {
    const val STALE_AFTER_MS = 30L * 60L * 1000L

    fun shouldRebind(
        lastNotificationAt: Long,
        now: Long = System.currentTimeMillis()
    ): Boolean = lastNotificationAt <= 0 || now - lastNotificationAt > STALE_AFTER_MS
}
