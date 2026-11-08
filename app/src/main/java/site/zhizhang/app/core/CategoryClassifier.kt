package site.zhizhang.app.core

data class CategoryDecision(val category: String, val needsAi: Boolean)

object CategoryClassifier {
    val defaultCategories = listOf(
        "餐饮美食", "购物消费", "交通出行", "居家生活", "医疗健康",
        "休闲娱乐", "教育学习", "通讯充值", "退款", "工资收入", "其他收入", "未分类"
    )

    private val rules = listOf(
        "餐饮美食" to listOf("肯德基", "麦当劳", "瑞幸", "星巴克", "奈雪", "咖啡", "餐厅", "餐饮", "火锅", "外卖", "美团外卖", "饿了么", "茶饮", "汉堡", "食堂"),
        "交通出行" to listOf("地铁", "公交", "滴滴", "打车", "出租车", "高铁", "铁路", "火车票", "航空", "机票", "加油", "停车", "共享单车", "哈啰", "ETC"),
        "居家生活" to listOf("山姆", "超市", "便利店", "水费", "电费", "燃气", "物业", "房租", "家政", "生鲜", "菜场"),
        "购物消费" to listOf("淘宝", "天猫", "京东", "拼多多", "抖音商城", "订单", "旗舰店", "百货", "商城", "购物"),
        "医疗健康" to listOf("医院", "药房", "药店", "门诊", "体检", "医疗", "挂号"),
        "休闲娱乐" to listOf("电影", "影院", "游戏", "音乐", "视频会员", "演出", "旅游", "酒店", "景区"),
        "教育学习" to listOf("书店", "图书", "课程", "培训", "学费", "考试"),
        "通讯充值" to listOf("中国移动", "中国联通", "中国电信", "话费", "流量充值"),
        "工资收入" to listOf("工资", "薪资", "奖金", "报销")
    )

    fun classify(merchant: String, rawText: String = "", isIncome: Boolean = false): CategoryDecision {
        if (isIncome) {
            if (merchant.contains("退款") || rawText.contains("退款")) return CategoryDecision("退款", false)
            val incomeMatch = rules.firstOrNull { it.first == "工资收入" }
                ?.second?.any { merchant.contains(it, true) || rawText.contains(it, true) } == true
            return CategoryDecision(if (incomeMatch) "工资收入" else "其他收入", false)
        }
        val haystack = "$merchant\n$rawText"
        rules.filterNot { it.first == "工资收入" }.forEach { (category, keywords) ->
            if (keywords.any { haystack.contains(it, ignoreCase = true) }) {
                val generic = merchant.isBlank() || merchant.endsWith("订单") || merchant.contains("待补充") ||
                    merchant in listOf("微信支付", "支付宝", "淘宝", "京东", "拼多多", "抖音")
                return CategoryDecision(category, generic && category != "餐饮美食")
            }
        }
        return CategoryDecision("未分类", true)
    }
}
