package site.zhizhang.app.core

import java.security.MessageDigest

/**
 * 统一交易方向：支出 / 收入 / 退款 / 转账。
 * Room 以枚举名字符串存储，旧数据只有 EXPENSE / INCOME，新增值向后兼容。
 * 统计口径：EXPENSE 计支出，INCOME 计收入，REFUND 冲减支出，TRANSFER 不计入收支。
 */
enum class PaymentType { EXPENSE, INCOME, REFUND, TRANSFER }
enum class ParseConfidence { HIGH, MEDIUM, LOW }

data class ParsedPayment(
    val amountCents: Long,
    val type: PaymentType,
    val merchant: String,
    val category: String,
    val needsAi: Boolean,
    val sourcePackage: String,
    val sourceName: String,
    val occurredAt: Long,
    val rawNotification: String,
    val confidence: ParseConfidence,
    val dedupKey: String
)

object PaymentParser {
    // 带货币符号的金额最可靠，优先匹配；关键词模式兜底（无 ¥ 的文案，如「金额12.50元」）
    // 关键词模式禁止跨行，且不允许把「[2条]」这类折叠计数当成金额
    private val amountPatterns = listOf(
        Regex("[¥￥]\\s*([0-9]{1,8}(?:\\.[0-9]{1,2})?)"),
        Regex("(?:实付款|实付|支付金额|付款金额|交易金额|金额|支付|付款|扣款|扣费|消费|收款|退款|转账|转出|合计|总计|订单金额)[^0-9¥￥\\n]{0,10}[¥￥]?\\s*([0-9]{1,8}(?:\\.[0-9]{1,2})?)")
    )

    private val foldCountPattern = Regex("\\[\\s*\\d+\\s*条\\s*\\]")
    private val merchantPatterns = listOf(
        Regex("(?:商户全称|商户名称|收款商户|交易商户|商户|收款方|商家|店铺)[:：\\s]+([^\\n，,。;；]{2,48})"),
        Regex("(?:向|给)([^\\n，,。;；]{2,32})(?:付款|支付|转账)"),
        Regex("(?:转账给|转给|付款给|支付给)([^\\n，,。;；¥￥0-9\\s]{2,20})"),
        Regex("(?:在|于)([^\\n，,。;；]{2,32})(?:消费|付款|支付)")
    )
    private val expenseWords = listOf(
        "支付成功", "付款成功", "已支付", "已付款", "扣款成功", "消费成功", "实付款", "实付", "成功支付",
        // 转账 / 电商订单常见句式（真机实测补入）
        "转账成功", "已转账", "转出成功", "已转出", "转账给", "转给", "付款给", "支付给",
        "订单支付成功", "订单已支付", "支付完成", "付款完成", "交易成功", "扣款", "已扣款", "消费成功",
        // 微信支付凭证的真实句式（小米真机实测：「[2条]微信支付: 已扣费¥5.98」）
        "已扣费", "扣费", "扣费成功", "自动扣费", "免密支付", "已扣款成功"
    )
    private val incomeWords = listOf("收款成功", "已收款", "转账到账", "已到账", "入账成功", "工资到账", "收到转账", "转账收入")
    private val refundWords = listOf("退款成功", "退款到账", "已退款")
    // 只过滤「明确不是一笔已完成的支出」的通知；不要放"待发货/待收货"，
    // 因为电商支付成功通知常附带这些状态词，放进去会误杀真实流水
    private val ignoreWords = listOf(
        "优惠券", "红包领取", "支付有礼", "待付款", "催付", "账单提醒", "还款提醒",
        "订单已取消", "支付已取消", "交易关闭", "退款申请", "订单已关闭", "订单取消"
    )

    fun parse(packageName: String, title: String, body: String, postedAt: Long): ParsedPayment? {
        val source = SupportedApps.find(packageName) ?: return null
        val raw = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n").take(2500)
        // 微信把多条消息折叠成一条通知时会加「[2条]」这种计数，先剥掉，避免被当成金额
        val compact = raw.replace(Regex("[ \\t\\r]+"), "").replace(foldCountPattern, "")
        if (compact.isBlank() || ignoreWords.any { compact.contains(it) }) return null

        val isRefund = refundWords.any { compact.contains(it) }
        val isIncome = isRefund || incomeWords.any { compact.contains(it) }
        val isExpense = expenseWords.any { compact.contains(it) }
        if (!isIncome && !isExpense) return null

        val amountText = amountPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(compact)?.groupValues?.getOrNull(1)
        } ?: return null
        val amountCents = ((amountText.toBigDecimalOrNull() ?: return null).movePointRight(2))
            .toLong()
        if (amountCents <= 0L || amountCents > 1_000_000_000L) return null

        var merchant = merchantPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(raw)?.groupValues?.getOrNull(1)?.let(::cleanMerchant)
        }.orEmpty()
        if (merchant.isBlank()) {
            merchant = when (packageName) {
                "com.taobao.taobao" -> "淘宝订单"
                "com.jingdong.app.mall" -> "京东订单"
                "com.xunmeng.pinduoduo" -> "拼多多订单"
                "com.ss.android.ugc.aweme" -> "抖音订单"
                else -> source.displayName
            }
        }
        val genericMerchant = merchant == source.displayName || merchant.endsWith("订单")
        val confidence = when {
            !genericMerchant && compact.contains(Regex("[¥￥]")) -> ParseConfidence.HIGH
            !genericMerchant -> ParseConfidence.MEDIUM
            else -> ParseConfidence.LOW
        }
        val decision = CategoryClassifier.classify(merchant, raw, isIncome)
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest("$packageName|$amountCents|${if (isIncome) "in" else "out"}|$merchant|${compact.take(600)}".toByteArray())
            .joinToString("") { "%02x".format(it) }

        return ParsedPayment(
            amountCents = amountCents,
            type = if (isIncome) PaymentType.INCOME else PaymentType.EXPENSE,
            merchant = merchant,
            category = decision.category,
            needsAi = decision.needsAi,
            sourcePackage = packageName,
            sourceName = source.displayName,
            occurredAt = postedAt,
            rawNotification = raw,
            confidence = confidence,
            dedupKey = fingerprint
        )
    }

    private fun cleanMerchant(value: String): String = value
        .trim()
        .removeSuffix("。")
        .replace(Regex("(?:订单详情|详情请点击|点击查看).*$"), "")
        .take(48)
        .trim()
}
