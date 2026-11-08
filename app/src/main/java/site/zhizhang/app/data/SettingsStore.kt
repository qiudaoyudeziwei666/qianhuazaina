package site.zhizhang.app.data

import android.content.Context
import android.content.SharedPreferences

class SettingsStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val keyStore = ApiKeyStore(context)

    var autoConfirm: Boolean
        get() = prefs.getBoolean("auto_confirm", true)
        set(value) = prefs.edit().putBoolean("auto_confirm", value).apply()

    var hideAiAmount: Boolean
        get() = prefs.getBoolean("hide_ai_amount", true)
        set(value) = prefs.edit().putBoolean("hide_ai_amount", value).apply()

    /** 仅调试用：允许把 ADB 注入的 com.android.shell 通知当作支付通知解析，默认关闭 */
    var acceptDebugNotifications: Boolean
        get() = prefs.getBoolean("accept_debug_notifications", false)
        set(value) = prefs.edit().putBoolean("accept_debug_notifications", value).apply()

    /** 前台服务常驻守护：默认开启，代价是通知栏有一条常驻提示 */
    var keepAliveEnabled: Boolean
        get() = prefs.getBoolean("keep_alive_enabled", true)
        set(value) = prefs.edit().putBoolean("keep_alive_enabled", value).apply()

    /** 每次识别到新流水立刻发一条提醒，避免「抓到了却看不见」 */
    var captureAlertEnabled: Boolean
        get() = prefs.getBoolean("capture_alert_enabled", true)
        set(value) = prefs.edit().putBoolean("capture_alert_enabled", value).apply()

    /** 监听服务最后一次收到系统通知的时间。界面退出、锁屏时同样会更新，是「后台还活着」的可观察信号 */
    var lastNotificationAt: Long
        get() = prefs.getLong("last_notification_at", 0L)
        set(value) = prefs.edit().putLong("last_notification_at", value).apply()

    /** 上一次主动请求系统重新绑定监听服务的时间（自愈记录，便于排查） */
    var lastRebindAt: Long
        get() = prefs.getLong("last_rebind_at", 0L)
        set(value) = prefs.edit().putLong("last_rebind_at", value).apply()

    /** 深色模式：false=浅色，true=深色（跟随手动开关，不强制跟随系统） */
    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", false)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    /** 首页卡片配置：逗号分隔的「卡片ID:是否显示」，顺序即展示顺序 */
    var homeCards: String
        get() = prefs.getString("home_cards", "").orEmpty()
        set(value) = prefs.edit().putString("home_cards", value).apply()

    /** AI 调用统计（本地计数，不含任何服务端数据） */
    var aiCallCount: Int
        get() = prefs.getInt("ai_call_count", 0)
        set(value) = prefs.edit().putInt("ai_call_count", value).apply()

    var aiEstimatedTokens: Long
        get() = prefs.getLong("ai_token_estimate", 0L)
        set(value) = prefs.edit().putLong("ai_token_estimate", value).apply()

    var monthlyBudgetCents: Long
        get() = prefs.getLong("monthly_budget", 1_120_000L)
        set(value) = prefs.edit().putLong("monthly_budget", value).apply()

    var apiBaseUrl: String
        get() = prefs.getString("api_base_url", "").orEmpty()
        set(value) = prefs.edit().putString("api_base_url", value.trim()).apply()

    var apiModel: String
        get() = prefs.getString("api_model", "").orEmpty()
        set(value) = prefs.edit().putString("api_model", value.trim()).apply()

    var apiKey: String
        get() = keyStore.read()
        set(value) = keyStore.write(value.trim())
}
