package site.zhizhang.app.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 首页「后台是否还活着」的文案。
 *
 * 通知监听是系统绑定的服务，不依赖 App 界面；但用户从界面上看不出来。
 * 所以用「监听服务上一次收到系统通知的时间」作为可观察的健康信号：
 * 退出界面、锁屏之后只要这个时间还在更新，就说明后台确实在工作。
 * 注意它表示「收到过通知」，不等于「记了一笔账」。
 */
object ActivityStatus {
    fun describe(enabled: Boolean, lastNotificationAt: Long, now: Long = System.currentTimeMillis()): String {
        if (!enabled) return "开启通知访问后才会自动识别"
        if (lastNotificationAt <= 0) return "已授权，尚未收到过任何通知"
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val lastCal = Calendar.getInstance().apply { timeInMillis = lastNotificationAt }
        val sameDay = nowCal.get(Calendar.YEAR) == lastCal.get(Calendar.YEAR) &&
            nowCal.get(Calendar.DAY_OF_YEAR) == lastCal.get(Calendar.DAY_OF_YEAR)
        val pattern = if (sameDay) "HH:mm" else "MM月dd日 HH:mm"
        val clock = SimpleDateFormat(pattern, Locale.CHINA).format(Date(lastNotificationAt))
        return "退出界面仍在后台 · 上次收到通知 $clock"
    }
}
