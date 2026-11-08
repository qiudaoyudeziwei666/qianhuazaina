package site.zhizhang.app.util

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 只记录「看起来像交易」的通知，用于排查为什么某笔支付没有自动入账。
 * 不记录普通聊天内容；明文仅保存在 App 私有目录，不上传、不参与备份。
 */
object NotificationLog {
    private const val FILE_NAME = "notification_log.jsonl"
    private const val MAX_LINES = 200

    private val tradeWords = listOf(
        "支付", "付款", "转账", "收款", "退款", "订单", "金额",
        "¥", "￥", "交易", "余额", "扣款", "扣费", "消费", "到账", "账单", "实付"
    )

    fun maybeRecord(
        context: Context,
        pkg: String,
        title: String,
        body: String,
        postedAt: Long,
        flags: Int,
        inWhitelist: Boolean,
        parsed: Boolean,
        parsedAmount: Long?,
        reason: String
    ) {
        val text = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n")
        if (text.isBlank() || tradeWords.none { text.contains(it) }) return

        val line = JSONObject().apply {
            put("ts", postedAt)
            put("pkg", pkg)
            put("title", title.take(200))
            put("text", body.take(800))
            put("flags", flags)
            put("inWhitelist", inWhitelist)
            put("parsed", parsed)
            put("amountCents", parsedAmount ?: 0)
            put("reason", reason)
        }.toString()

        synchronized(this) {
            runCatching {
                val file = File(context.applicationContext.filesDir, FILE_NAME)
                val old = if (file.exists()) file.readLines().takeLast(MAX_LINES - 1) else emptyList()
                file.writeText((old + line).joinToString("\n") + "\n")
            }
        }
    }

    /** 供「通知诊断」界面读取，返回最近若干条人类可读的记录 */
    fun recent(context: Context, limit: Int = 20): List<LogItem> = synchronized(this) {
        runCatching {
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            if (!file.exists()) return emptyList()
            file.readLines().takeLast(limit).reversed().mapNotNull { line ->
                runCatching {
                    val o = JSONObject(line)
                    LogItem(
                        time = fmt.format(Date(o.optLong("ts"))),
                        pkg = o.optString("pkg"),
                        title = o.optString("title"),
                        text = o.optString("text"),
                        inWhitelist = o.optBoolean("inWhitelist"),
                        parsed = o.optBoolean("parsed"),
                        amount = o.optLong("amountCents"),
                        reason = o.optString("reason")
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    fun clear(context: Context) {
        synchronized(this) {
            runCatching { File(context.applicationContext.filesDir, FILE_NAME).delete() }
        }
    }

    data class LogItem(
        val time: String,
        val pkg: String,
        val title: String,
        val text: String,
        val inWhitelist: Boolean,
        val parsed: Boolean,
        val amount: Long,
        val reason: String
    )

    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
}
