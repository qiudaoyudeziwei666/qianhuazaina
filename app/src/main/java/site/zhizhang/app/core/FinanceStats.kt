package site.zhizhang.app.core

import site.zhizhang.app.data.EntryStatus
import site.zhizhang.app.data.LedgerEntry
import java.util.Calendar
import kotlin.math.ceil
import kotlin.math.max

/**
 * 全局统一统计口径（纯 JVM 函数，便于单元测试）：
 * - 只统计 CONFIRMED；PENDING / IGNORED 一律不进入任何图表或汇总。
 * - EXPENSE 计支出，INCOME 计收入，REFUND 冲减支出，TRANSFER 不计入收支。
 * - 金额一律 Long「分」，绝不使用浮点参与汇总。
 */
object FinanceStats {

    data class MonthSummary(
        val year: Int,
        val month: Int,
        val incomeCents: Long,
        val expenseCents: Long,
        val count: Int,
        val daysElapsed: Int,
        val daysInMonth: Int
    ) {
        val balanceCents: Long get() = incomeCents - expenseCents
        val label: String get() = "${month}月"
    }

    data class Dashboard(
        val month: MonthSummary,
        val previous: MonthSummary?,
        val expenseChange: Double?,
        val incomeChange: Double?,
        val dailyAverageCents: Long,
        val budgetCents: Long,
        val budgetRemainingCents: Long,
        val forecastCents: Long,
        val trendCents: List<Long>,
        val savingsRate: Double?,
        val transferCents: Long
    )

    fun confirmed(entries: List<LedgerEntry>): List<LedgerEntry> =
        entries.filter { it.status == EntryStatus.CONFIRMED }

    private fun cal(ts: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ts }

    fun monthBounds(year: Int, month1Based: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month1Based - 1)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            timeInMillis = start
            add(Calendar.MONTH, 1)
        }.timeInMillis
        return start to end
    }

    fun inMonth(ts: Long, year: Int, month1Based: Int): Boolean {
        val (s, e) = monthBounds(year, month1Based)
        return ts in s until e
    }

    /** 净支出：支出 - 退款；转账与收入都不计入 */
    fun netExpenseCents(entry: LedgerEntry): Long = when (entry.type) {
        PaymentType.EXPENSE -> entry.amountCents
        PaymentType.REFUND -> -entry.amountCents
        else -> 0L
    }

    fun summary(entries: List<LedgerEntry>, year: Int, month: Int, now: Long = System.currentTimeMillis()): MonthSummary {
        val (start, end) = monthBounds(year, month)
        val list = confirmed(entries).filter { it.occurredAt in start until end }
        var income = 0L
        var expense = 0L
        list.forEach {
            when (it.type) {
                PaymentType.INCOME -> income += it.amountCents
                PaymentType.EXPENSE -> expense += it.amountCents
                PaymentType.REFUND -> expense -= it.amountCents
                PaymentType.TRANSFER -> Unit
            }
        }
        val c = Calendar.getInstance().apply {
            timeInMillis = start
            set(Calendar.DAY_OF_MONTH, 1)
        }
        val daysInMonth = c.getActualMaximum(Calendar.DAY_OF_MONTH)
        val nowCal = cal(now)
        val isCurrent = nowCal.get(Calendar.YEAR) == year && nowCal.get(Calendar.MONTH) + 1 == month
        val daysElapsed = if (isCurrent) nowCal.get(Calendar.DAY_OF_MONTH) else daysInMonth
        return MonthSummary(year, month, income, expense, list.size, daysElapsed, daysInMonth)
    }

    /** 最近 months 个月（含本月）的序列，按时间正序 */
    fun series(entries: List<LedgerEntry>, months: Int, now: Long = System.currentTimeMillis()): List<MonthSummary> {
        val base = cal(now)
        return (months - 1 downTo 0).map { offset ->
            val c = Calendar.getInstance().apply {
                timeInMillis = base.timeInMillis
                add(Calendar.MONTH, -offset)
            }
            summary(entries, c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, now)
        }
    }

    /** 环比：上期为 0 时返回 null（不伪造百分比） */
    fun changeRate(current: Long, previous: Long): Double? =
        if (previous == 0L) null else (current - previous).toDouble() / previous

    fun dailyAverage(expenseCents: Long, daysElapsed: Int): Long =
        if (daysElapsed <= 0) 0L else expenseCents / daysElapsed

    /** 按当月消费速度线性预测月末支出；daysElapsed=0 时返回已花金额 */
    fun forecast(spentCents: Long, daysElapsed: Int, daysInMonth: Int): Long =
        if (daysElapsed <= 0 || daysInMonth <= 0) spentCents
        else (spentCents.toDouble() * daysInMonth / daysElapsed).toLong()

    fun categoryTotals(entries: List<LedgerEntry>, year: Int, month: Int): List<Pair<String, Long>> {
        val (s, e) = monthBounds(year, month)
        val map = linkedMapOf<String, Long>()
        confirmed(entries).filter { it.occurredAt in s until e }.forEach {
            val net = netExpenseCents(it)
            if (net != 0L) map[it.category] = (map[it.category] ?: 0L) + net
        }
        return map.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    fun dailyTotals(entries: List<LedgerEntry>, year: Int, month: Int): Map<Int, Long> {
        val (s, e) = monthBounds(year, month)
        val map = linkedMapOf<Int, Long>()
        confirmed(entries).filter { it.occurredAt in s until e }.forEach {
            val net = netExpenseCents(it)
            if (net != 0L) {
                val day = cal(it.occurredAt).get(Calendar.DAY_OF_MONTH)
                map[day] = (map[day] ?: 0L) + net
            }
        }
        return map
    }

    fun merchantRanking(entries: List<LedgerEntry>, year: Int, month: Int, limit: Int = 8): List<Pair<String, Long>> {
        val (s, e) = monthBounds(year, month)
        val map = linkedMapOf<String, Long>()
        confirmed(entries).filter { it.occurredAt in s until e && it.type != PaymentType.TRANSFER }.forEach {
            val net = netExpenseCents(it)
            if (net > 0) map[it.merchant] = (map[it.merchant] ?: 0L) + net
        }
        return map.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }
    }

    /** 固定支出 / 非固定支出（由用户定义哪些分类算固定） */
    fun fixedVsVariable(
        entries: List<LedgerEntry>, year: Int, month: Int, fixedCategories: Set<String>
    ): Pair<Long, Long> {
        val (s, e) = monthBounds(year, month)
        var fixed = 0L
        var variable = 0L
        confirmed(entries).filter { it.occurredAt in s until e }.forEach {
            val net = netExpenseCents(it)
            if (net > 0) {
                if (it.category in fixedCategories) fixed += net else variable += net
            }
        }
        return fixed to variable
    }

    fun transferTotal(entries: List<LedgerEntry>, year: Int, month: Int): Long {
        val (s, e) = monthBounds(year, month)
        return confirmed(entries).filter {
            it.occurredAt in s until e && it.type == PaymentType.TRANSFER
        }.sumOf { it.amountCents }
    }

    fun annualSubscriptionCost(amountCents: Long, cycle: String): Long = when (cycle.uppercase()) {
        "MONTHLY" -> amountCents * 12
        "QUARTERLY" -> amountCents * 4
        "YEARLY" -> amountCents
        else -> amountCents * 12
    }

    fun subscriptionMonthlyEquivalent(amountCents: Long, cycle: String): Long =
        annualSubscriptionCost(amountCents, cycle) / 12

    /** 按当前月储蓄速度推算还需几个月；速度 <= 0 返回 null（不猜测） */
    fun monthsToGoal(remainingCents: Long, monthlySavingCents: Long): Int? =
        if (remainingCents <= 0) 0
        else if (monthlySavingCents <= 0) null
        else ceil(remainingCents.toDouble() / monthlySavingCents).toInt()

    fun savingsRate(incomeCents: Long, expenseCents: Long): Double? =
        if (incomeCents <= 0) null else (incomeCents - expenseCents).toDouble() / incomeCents

    fun buildDashboard(
        entries: List<LedgerEntry>,
        budgetCents: Long,
        now: Long = System.currentTimeMillis()
    ): Dashboard {
        val c = cal(now)
        val year = c.get(Calendar.YEAR)
        val month = c.get(Calendar.MONTH) + 1
        val cur = summary(entries, year, month, now)
        val prevCal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, -1) }
        val prev = summary(entries, prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH) + 1, now)
        return Dashboard(
            month = cur,
            previous = prev,
            expenseChange = changeRate(cur.expenseCents, prev.expenseCents),
            incomeChange = changeRate(cur.incomeCents, prev.incomeCents),
            dailyAverageCents = dailyAverage(cur.expenseCents, cur.daysElapsed),
            budgetCents = budgetCents,
            budgetRemainingCents = budgetCents - cur.expenseCents,
            forecastCents = forecast(cur.expenseCents, cur.daysElapsed, cur.daysInMonth),
            trendCents = dailyTotals(entries, year, month).let { daily ->
                (1..cur.daysElapsed).map { day -> daily[day] ?: 0L }
            },
            savingsRate = savingsRate(cur.incomeCents, cur.expenseCents),
            transferCents = transferTotal(entries, year, month)
        )
    }

    /** 确定性消费洞察：只基于真实已确认账单，不使用虚构数据、不调用 AI */
    fun insightLines(
        entries: List<LedgerEntry>,
        budgetCents: Long,
        fixedCategories: Set<String>,
        nonEssentialCategories: Set<String>,
        now: Long = System.currentTimeMillis()
    ): List<String> {
        val c = cal(now)
        val year = c.get(Calendar.YEAR); val month = c.get(Calendar.MONTH) + 1
        val cur = summary(entries, year, month, now)
        val prevCal = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, -1) }
        val prev = summary(entries, prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH) + 1, now)
        val lines = mutableListOf<String>()
        if (cur.count == 0) return listOf("本月还没有已确认流水，先记一笔或确认待确认账单。")

        val rate = changeRate(cur.expenseCents, prev.expenseCents)
        if (rate != null) {
            val pct = (rate * 100).toInt()
            lines += if (pct >= 0) "本月支出较上月增加 $pct%，注意消费节奏。" else "本月支出较上月减少 ${-pct}%，保持得不错。"
        }
        val avg = dailyAverage(cur.expenseCents, cur.daysElapsed)
        val maxDay = dailyTotals(entries, year, month).maxByOrNull { it.value }
        if (maxDay != null && maxDay.value > avg * 3 && avg > 0) {
            lines += "${maxDay.key} 日支出 ¥${"%,.2f".format(maxDay.value / 100.0)}，超过日均 3 倍，属异常大额日。"
        }
        val top = merchantRanking(entries, year, month, 1).firstOrNull()
        if (top != null) lines += "最常消费商户：${top.first}，本月累计 ¥${"%,.2f".format(top.second / 100.0)}。"
        val (fixed, variable) = fixedVsVariable(entries, year, month, fixedCategories)
        if (fixed + variable > 0) {
            lines += "固定支出占 ${(fixed * 100 / (fixed + variable))}%（按你标记的分类计算）。"
        }
        if (nonEssentialCategories.isNotEmpty()) {
            val nonEssential = categoryTotals(entries, year, month)
                .filter { it.first in nonEssentialCategories && it.second > 0 }
                .sumOf { it.second }
            val total = max(cur.expenseCents, 1L)
            lines += "非必要消费占比约 ${(nonEssential * 100 / total)}%。"
        }
        if (budgetCents > 0) {
            val fc = forecast(cur.expenseCents, cur.daysElapsed, cur.daysInMonth)
            val remain = budgetCents - cur.expenseCents
            lines += if (fc > budgetCents) "按当前速度，月末预计超支 ¥${"%,.2f".format((fc - budgetCents) / 100.0)}，建议收紧非必要支出。"
            else "预算剩余 ¥${"%,.2f".format(max(remain, 0L) / 100.0)}，按当前速度可控制在预算内。"
        }
        savingsRate(cur.incomeCents, cur.expenseCents)?.let {
            lines += "本月结余率 ${(it * 100).toInt()}%。"
        }
        return lines
    }
}
