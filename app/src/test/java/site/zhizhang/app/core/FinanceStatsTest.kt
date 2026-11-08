package site.zhizhang.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.zhizhang.app.data.EntryStatus
import site.zhizhang.app.data.LedgerEntry
import java.util.Calendar

class FinanceStatsTest {

    private fun ts(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, 12, 0, 0)
    }.timeInMillis

    private val now = ts(2026, 11, 15)

    private fun entry(
        amount: Long,
        type: PaymentType = PaymentType.EXPENSE,
        status: EntryStatus = EntryStatus.CONFIRMED,
        day: Int = 5,
        category: String = "餐饮美食",
        merchant: String = "测试商户"
    ) = LedgerEntry(
        amountCents = amount,
        type = type,
        merchant = merchant,
        category = category,
        sourcePackage = "manual",
        sourceName = "手动",
        occurredAt = ts(2026, 11, day),
        status = status
    )

    @Test
    fun `only confirmed entries count`() {
        val list = listOf(
            entry(1000),
            entry(9999, status = EntryStatus.PENDING),
            entry(9999, status = EntryStatus.IGNORED)
        )
        val s = FinanceStats.summary(list, 2026, 11, now)
        assertEquals(1000L, s.expenseCents)
        assertEquals(1, s.count)
    }

    @Test
    fun `refund offsets expense and transfer is excluded`() {
        val list = listOf(
            entry(1000),
            entry(200, type = PaymentType.REFUND),
            entry(5000, type = PaymentType.TRANSFER),
            entry(8000, type = PaymentType.INCOME)
        )
        val s = FinanceStats.summary(list, 2026, 11, now)
        assertEquals(800L, s.expenseCents)
        assertEquals(8000L, s.incomeCents)
        assertEquals(7200L, s.balanceCents)
    }

    @Test
    fun `pending entries never appear in category totals or heat map`() {
        val list = listOf(entry(1000), entry(5000, status = EntryStatus.PENDING, category = "购物消费"))
        val cats = FinanceStats.categoryTotals(list, 2026, 11)
        assertEquals(1000L, cats.sumOf { it.second })
        assertEquals(1000L, FinanceStats.dailyTotals(list, 2026, 11).values.sum())
    }

    @Test
    fun `change rate is null when previous period is zero`() {
        assertNull(FinanceStats.changeRate(1000, 0))
        assertEquals(0.5, FinanceStats.changeRate(1500, 1000)!!, 0.0001)
        assertEquals(-0.25, FinanceStats.changeRate(750, 1000)!!, 0.0001)
    }

    @Test
    fun `daily average and linear forecast use integer cents`() {
        assertEquals(100L, FinanceStats.dailyAverage(1500L, 15))
        assertEquals(3000L, FinanceStats.forecast(1500L, 15, 30))
        assertEquals(1500L, FinanceStats.forecast(1500L, 0, 30))
    }

    @Test
    fun `annual subscription cost by cycle`() {
        assertEquals(2400L, FinanceStats.annualSubscriptionCost(200, "MONTHLY"))
        assertEquals(2400L, FinanceStats.annualSubscriptionCost(600, "QUARTERLY"))
        assertEquals(1200L, FinanceStats.annualSubscriptionCost(1200, "YEARLY"))
    }

    @Test
    fun `category totals sum equals net expense`() {
        val list = listOf(
            entry(1000, category = "餐饮美食"),
            entry(2500, category = "购物消费", merchant = "拼多多"),
            entry(300, type = PaymentType.REFUND, category = "餐饮美食")
        )
        val total = FinanceStats.categoryTotals(list, 2026, 11).sumOf { it.second }
        assertEquals(3200L, total)
        assertEquals(3200L, FinanceStats.summary(list, 2026, 11, now).expenseCents)
    }

    @Test
    fun `fixed vs variable split respects user defined categories`() {
        val list = listOf(
            entry(1500, category = "居家生活"),
            entry(300, category = "餐饮美食")
        )
        val (fixed, variable) = FinanceStats.fixedVsVariable(list, 2026, 11, setOf("居家生活"))
        assertEquals(1500L, fixed)
        assertEquals(300L, variable)
    }

    @Test
    fun `months to goal and savings rate`() {
        assertEquals(4, FinanceStats.monthsToGoal(3500, 1000))
        assertEquals(0, FinanceStats.monthsToGoal(0, 1000))
        assertNull(FinanceStats.monthsToGoal(3500, 0))
        assertEquals(0.2, FinanceStats.savingsRate(10000, 8000)!!, 0.0001)
        assertNull(FinanceStats.savingsRate(0, 8000))
    }

    @Test
    fun `dashboard uses confirmed data and keeps budget remaining accurate`() {
        val list = listOf(entry(2000), entry(9000, type = PaymentType.INCOME))
        val d = FinanceStats.buildDashboard(list, 5200L, now)
        assertEquals(2000L, d.month.expenseCents)
        assertEquals(3200L, d.budgetRemainingCents)
        assertEquals(15, d.trendCents.size)
        assertTrue(d.forecastCents >= d.month.expenseCents)
    }

    @Test
    fun `insights do not invent data for empty ledgers`() {
        val lines = FinanceStats.insightLines(emptyList(), 5200L, emptySet(), emptySet(), now)
        assertEquals(1, lines.size)
        assertTrue(lines.first().contains("还没有"))
    }
}
