package site.zhizhang.app.core

/**
 * 判断一条流水「信息是否完整」。
 *
 * 背景：微信/支付宝的扣费凭证通知常常只有金额（例如「已扣费¥5.98」），
 * 既没有商户名，也没有买了什么。商品明细存在电商的订单系统里，
 * 不在通知里 —— 这是数据源缺失，不是解析算法能补出来的。
 * 这样的流水不该被伪装成一条完整的账，必须明确标记出来让用户补全。
 */
data class Completeness(
    val missingMerchant: Boolean,
    val missingCategory: Boolean,
    val missingItem: Boolean
) {
    val needsDetail: Boolean get() = missingMerchant || missingCategory || missingItem

    val summary: String
        get() {
            val parts = mutableListOf<String>()
            if (missingMerchant) parts.add("商户")
            if (missingCategory) parts.add("分类")
            if (missingItem) parts.add("买了什么")
            return if (parts.isEmpty()) "" else "缺" + parts.joinToString(" / ")
        }
}

object EntryCompleteness {
    private val genericMerchants = setOf(
        "微信支付", "支付宝", "微信", "待补充", "未知商户", "手动记账",
        "拼多多订单", "淘宝订单", "京东订单", "抖音订单", "美团订单"
    )

    /** 渠道名或「XX订单」这类占位名，都不算真正的商户信息 */
    fun isGenericMerchant(merchant: String): Boolean {
        val m = merchant.trim()
        if (m.isEmpty()) return true
        if (m in genericMerchants) return true
        return m.endsWith("订单") && m.length <= 8
    }

    fun check(merchant: String, category: String, note: String): Completeness = Completeness(
        missingMerchant = isGenericMerchant(merchant),
        missingCategory = category.trim().isEmpty() || category.trim() == "未分类",
        missingItem = note.trim().isEmpty()
    )
}
