package site.zhizhang.app.core

/**
 * 分层分类引擎（纯 JVM，不联网）：
 * 1. 用户自定义规则（最高优先，置信度 95）
 * 2. 本地商户关键词规则（复用 CategoryClassifier，置信度 80）
 * 3. 历史已确认消费记录推断（置信度 60）
 * 4. 仍无法识别 → 未分类 + needsAi=true，由用户决定是否调用 AI
 */
object CategoryEngine {

    data class Decision(
        val category: String,
        val confidence: Int,
        val source: String,
        val needsAi: Boolean,
        val matchedBy: String = ""
    )

    fun classify(
        merchant: String,
        rawText: String = "",
        isIncome: Boolean = false,
        userRules: List<Pair<String, String>> = emptyList(),
        history: List<Pair<String, String>> = emptyList()
    ): Decision {
        val haystack = "$merchant\n$rawText"
        if (isIncome) {
            val local = CategoryClassifier.classify(merchant, rawText, true)
            return Decision(local.category, 80, "LOCAL", false, "收入规则")
        }

        // ① 用户自定义规则：长关键词优先，避免「美团」覆盖「美团外卖」
        val matchedRule = userRules
            .filter { it.first.isNotBlank() && haystack.contains(it.first, ignoreCase = true) }
            .maxByOrNull { it.first.length }
        if (matchedRule != null) {
            return Decision(matchedRule.second, 95, "USER_RULE", false, matchedRule.first)
        }

        // ② 内置本地关键词
        val local = CategoryClassifier.classify(merchant, rawText, false)
        if (local.category != "未分类") {
            return Decision(local.category, 80, "LOCAL", false, "商户关键词")
        }

        // ③ 历史消费记录：先精确匹配商户，再退化为包含匹配，取出现次数最多的分类
        val exact = history.filter { it.first.equals(merchant, ignoreCase = true) && it.second != "未分类" }
        val candidate = if (exact.isNotEmpty()) exact else history.filter {
            it.second != "未分类" && merchant.isNotBlank() &&
                (it.first.contains(merchant, true) || merchant.contains(it.first, true))
        }
        val best = candidate.groupingBy { it.second }.eachCount().maxByOrNull { it.value }
        if (best != null) return Decision(best.key, 60, "HISTORY", false, "历史记录")

        return Decision("未分类", 0, "NONE", true)
    }
}
