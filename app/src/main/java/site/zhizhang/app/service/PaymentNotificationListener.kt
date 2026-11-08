package site.zhizhang.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import site.zhizhang.app.BuildConfig
import site.zhizhang.app.core.CategoryEngine
import site.zhizhang.app.core.ParsedPayment
import site.zhizhang.app.core.PaymentParser
import site.zhizhang.app.core.PaymentType
import site.zhizhang.app.core.SupportedApps
import site.zhizhang.app.data.LedgerDatabase
import site.zhizhang.app.data.LedgerRepository
import site.zhizhang.app.data.SettingsStore
import site.zhizhang.app.util.NotificationLog
import site.zhizhang.app.widget.LedgerWidgetProvider

class PaymentNotificationListener : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository by lazy { LedgerRepository(LedgerDatabase.get(this).ledgerDao()) }
    private val financeDao by lazy { LedgerDatabase.get(this).financeDao() }
    private val settings by lazy { SettingsStore(this) }

    /** 入库前套用分层分类：用户自定义规则 > 本地关键词 > 历史 > 待 AI */
    private suspend fun insertWithRules(parsed: ParsedPayment, autoConfirm: Boolean): Boolean {
        val rules = runCatching { financeDao.rulesOnce() }.getOrDefault(emptyList())
            .map { it.keyword to it.category }
        val decision = CategoryEngine.classify(
            merchant = parsed.merchant,
            rawText = parsed.rawNotification,
            isIncome = parsed.type == PaymentType.INCOME,
            userRules = rules
        )
        val adjusted = parsed.copy(category = decision.category, needsAi = decision.needsAi)
        return repository.insertParsed(adjusted, autoConfirm)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // 回调能发生，说明系统绑定的监听服务还活着；退出界面、锁屏之后一样会更新
        settings.lastNotificationAt = System.currentTimeMillis()
        val packageName = sbn.packageName ?: return
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = buildList {
            listOf(
                Notification.EXTRA_TEXT,
                Notification.EXTRA_BIG_TEXT,
                Notification.EXTRA_SUB_TEXT,
                Notification.EXTRA_SUMMARY_TEXT
            ).forEach { key -> extras.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }?.let(::add) }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it.toString()) }
        }.distinct().joinToString("\n")

        // 调试注入：仅当 ADB 通知带专用标记（tag 以 ZhizhangSim 开头）或设置页手动打开开关时生效。
        // 普通用户日常不会收到 com.android.shell 的通知，因此不会误收；用于不实际付款验证整条链路。
        val tagged = sbn.tag?.startsWith("ZhizhangSim") == true
        // 调试旁路只在 Debug 构建存在；正式发布包里恒为 false，没有任何后门
        val isDebugInjection = BuildConfig.DEBUG && packageName == "com.android.shell" &&
            (tagged || settings.acceptDebugNotifications)
        val effectivePackage = if (isDebugInjection) {
            when {
                title.contains("支付宝") || body.contains("支付宝") -> "com.eg.android.AlipayGphone"
                title.contains("拼多多") || body.contains("拼多多") -> "com.xunmeng.pinduoduo"
                else -> "com.tencent.mm"
            }
        } else packageName

        val source = SupportedApps.find(effectivePackage)
        val isGroupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0

        // 先解析（不改动账本）再记诊断日志，这样「通知到了但没入账」也能被看见
        val parsed = if (source != null) PaymentParser.parse(effectivePackage, title, body, sbn.postTime) else null
        val reason = when {
            isDebugInjection -> "ADB 调试注入（映射到 ${source?.displayName}）"
            source == null -> "包名不在白名单（可能是厂商推送代发）"
            isGroupSummary -> "分组摘要通知，按设计跳过"
            parsed == null -> "文案未匹配支付规则或缺少金额"
            else -> "已解析，准备入账"
        }
        NotificationLog.maybeRecord(
            context = this,
            pkg = packageName,
            title = title,
            body = body,
            postedAt = sbn.postTime,
            flags = notification.flags,
            inWhitelist = source != null,
            parsed = parsed != null,
            parsedAmount = parsed?.amountCents,
            reason = reason
        )

        if (isDebugInjection) {
            // 调试注入固定走「待确认」，避免污染真实统计，也方便看清解析结果
            parsed?.let { p ->
                serviceScope.launch {
                    val inserted = insertWithRules(p, autoConfirm = false)
                    if (inserted) {
                        GuardForegroundService.showCapture(
                            this@PaymentNotificationListener, p.amountCents, p.sourceName, p.merchant, true
                        )
                        LedgerWidgetProvider.updateAll(this@PaymentNotificationListener)
                    }
                }
            }
            return
        }

        if (source == null || isGroupSummary || parsed == null) return

        serviceScope.launch {
            val inserted = insertWithRules(parsed, settings.autoConfirm)
            if (inserted) {
                // 立刻提醒，别让用户以为「退出后没抓到」
                GuardForegroundService.showCapture(
                    this@PaymentNotificationListener, parsed.amountCents, parsed.sourceName, parsed.merchant, false
                )
                LedgerWidgetProvider.updateAll(this@PaymentNotificationListener)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
