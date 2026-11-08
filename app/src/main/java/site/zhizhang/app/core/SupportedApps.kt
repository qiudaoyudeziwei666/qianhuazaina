package site.zhizhang.app.core

data class PaymentSource(
    val packageName: String,
    val displayName: String,
    val shortName: String,
    val supportsRefund: Boolean = true
)

object SupportedApps {
    val sources = listOf(
        PaymentSource("com.tencent.mm", "微信支付", "微"),
        PaymentSource("com.eg.android.AlipayGphone", "支付宝", "支"),
        PaymentSource("com.taobao.taobao", "淘宝", "淘"),
        PaymentSource("com.jingdong.app.mall", "京东", "京"),
        PaymentSource("com.xunmeng.pinduoduo", "拼多多", "拼"),
        PaymentSource("com.sankuai.meituan", "美团", "美"),
        PaymentSource("com.ss.android.ugc.aweme", "抖音", "抖")
    )

    private val byPackage = sources.associateBy { it.packageName }

    fun find(packageName: String): PaymentSource? = byPackage[packageName]

    fun displayName(packageName: String): String = find(packageName)?.displayName ?: "手动记录"
}
