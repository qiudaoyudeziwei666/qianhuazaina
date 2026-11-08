package site.zhizhang.app.core

/** 首页卡片顺序与显示状态的纯函数配置（可单元测试，不含 Android 依赖） */
object HomeCards {
    val ids = listOf(
        "SUMMARY", "QUICK", "PENDING", "BUDGET", "TREND",
        "SAVINGS", "INSIGHTS", "AUTO", "RECENT"
    )

    val labels = mapOf(
        "SUMMARY" to "收支总览",
        "QUICK" to "常用功能",
        "PENDING" to "待确认流水",
        "BUDGET" to "预算健康度",
        "TREND" to "消费趋势",
        "SAVINGS" to "储蓄进度",
        "INSIGHTS" to "消费洞察",
        "AUTO" to "自动记账状态",
        "RECENT" to "最近账单"
    )

    fun defaults(): List<Pair<String, Boolean>> = ids.map { it to true }

    /** 容错解析：未知 ID 忽略，缺失 ID 用默认追加，顺序按已保存的排在前面 */
    fun parse(raw: String): List<Pair<String, Boolean>> {
        if (raw.isBlank()) return defaults()
        val saved = raw.split(",").mapNotNull { part ->
            val kv = part.split(":")
            if (kv.size == 2 && kv[0] in ids) kv[0] to (kv[1] == "1") else null
        }.toMap()
        val order = saved.keys.toList() + ids.filterNot { it in saved }
        return order.map { it to (saved[it] ?: true) }
    }

    fun serialize(list: List<Pair<String, Boolean>>): String =
        list.joinToString(",") { "${it.first}:${if (it.second) 1 else 0}" }

    fun move(list: List<Pair<String, Boolean>>, index: Int, up: Boolean): List<Pair<String, Boolean>> {
        val target = index + if (up) -1 else 1
        if (index !in list.indices || target !in list.indices) return list
        return list.toMutableList().also { java.util.Collections.swap(it, index, target) }
    }

    fun toggle(list: List<Pair<String, Boolean>>, index: Int): List<Pair<String, Boolean>> =
        list.mapIndexed { i, p -> if (i == index) p.first to !p.second else p }

    fun visible(list: List<Pair<String, Boolean>>): List<String> =
        list.filter { it.second }.map { it.first }
}
