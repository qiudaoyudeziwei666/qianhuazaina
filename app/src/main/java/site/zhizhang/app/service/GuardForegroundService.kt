package site.zhizhang.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import site.zhizhang.app.MainActivity
import site.zhizhang.app.data.SettingsStore

/**
 * 前台服务常驻守护（不 Root，Android 官方允许的方式）。
 *
 * 为什么需要：通知监听服务虽然由系统绑定，但小米/HyperOS 在「划掉最近任务卡片」
 * 或一键清理时会把 App 整个进程杀掉，导致通知回调中断。前台服务会显著提高进程优先级，
 * START_STICKY + onTaskRemoved 再补一层自我拉起。
 *
 * 代价：通知栏有一条静默常驻提示，用户可在「我的」里关掉。
 * 边界：用户强行停止、系统冻结、卸载依然可以停掉它 —— 这是平台规则，无法绕过。
 */
class GuardForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!SettingsStore(this).keepAliveEnabled) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        runCatching { startForeground(NOTIF_GUARD, guardNotification()) }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 划掉最近任务卡片时尝试自我拉起；Android 12+ 可能限制后台启动，
        // 失败也没关系，下次打开 App 会再启动一次。
        runCatching { start(this) }
        super.onTaskRemoved(rootIntent)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_GUARD, "后台守护", NotificationManager.IMPORTANCE_LOW).apply {
                description = "保持通知识别服务常驻，静默无声音"
                setSound(null, null)
                enableVibration(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_CAPTURE, "抓到新流水", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "每识别到一笔支付通知就提醒你核对"
            }
        )
    }

    private fun openAppPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        pendingIntentFlags()
    )

    private fun guardNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_GUARD) else Notification.Builder(this)
        return builder
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("知账后台守护中")
            .setContentText("退出界面也会继续识别支付通知")
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent())
            .build()
    }

    companion object {
        const val CHANNEL_GUARD = "zhizhang_guard"
        const val CHANNEL_CAPTURE = "zhizhang_capture"
        const val NOTIF_GUARD = 1001

        private fun pendingIntentFlags(): Int =
            PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        /** 从前台界面或开机广播调用；后台启动被系统拒绝时静默忽略，等下次打开 App 再试 */
        fun start(context: Context) {
            if (!SettingsStore(context).keepAliveEnabled) return
            runCatching {
                val intent = Intent(context, GuardForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, GuardForegroundService::class.java)) }
        }

        /** 识别到新流水立刻提醒，解决「抓到了但首页没变化、以为没记」的问题 */
        fun showCapture(
            context: Context,
            amountCents: Long,
            source: String,
            merchant: String,
            isDebug: Boolean
        ) {
            if (!SettingsStore(context).captureAlertEnabled) return
            runCatching {
                val nm = context.getSystemService(NotificationManager::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    nm.getNotificationChannel(CHANNEL_CAPTURE) == null
                ) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL_CAPTURE, "抓到新流水", NotificationManager.IMPORTANCE_DEFAULT)
                    )
                }
                val amount = "%,.2f".format(amountCents / 100.0)
                val prefix = if (isDebug) "[测试] " else ""
                val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    Notification.Builder(context, CHANNEL_CAPTURE) else Notification.Builder(context)
                val pi = PendingIntent.getActivity(
                    context, 0,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    pendingIntentFlags()
                )
                val notification = builder
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("${prefix}知账抓到一笔 ¥$amount")
                    .setContentText("$source · ${merchant.ifBlank { "待补充" }} · 待确认，点开补全")
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build()
                nm.notify((System.currentTimeMillis() % 100000).toInt(), notification)
            }
        }
    }
}
