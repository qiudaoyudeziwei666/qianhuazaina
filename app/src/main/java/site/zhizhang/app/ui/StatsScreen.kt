package site.zhizhang.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import site.zhizhang.app.core.FinanceStats
import site.zhizhang.app.core.PaymentType
import site.zhizhang.app.data.LedgerEntry
import java.util.Calendar

/**
 * 独立统计分析页：图表全部 Canvas 自绘、适配手机宽度、支持点击联动查看流水。
 * 所有图表只统计 CONFIRMED，PENDING 单独显示，避免重复计算。
 */
@Composable
fun StatsScreen(
    entries: List<LedgerEntry>,
    pending: List<LedgerEntry>,
    fixedCategories: Set<String>,
    onExport: () -> Unit,
    onEntryClick: (LedgerEntry) -> Unit,
    now: Long = System.currentTimeMillis()
) {
    val base = Calendar.getInstance().apply { timeInMillis = now }
    var year by remember { mutableIntStateOf(base.get(Calendar.YEAR)) }
    var month by remember { mutableIntStateOf(base.get(Calendar.MONTH) + 1) }
    var monthsBack by remember { mutableIntStateOf(6) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedMerchant by remember { mutableStateOf<String?>(null) }

    val cur = FinanceStats.summary(entries, year, month, now)
    val prevCal = Calendar.getInstance().apply { set(year, month - 1, 1); add(Calendar.MONTH, -1) }
    val prev = FinanceStats.summary(entries, prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH) + 1, now)
    val series = FinanceStats.series(entries, monthsBack, now)
    val categoryTotals = FinanceStats.categoryTotals(entries, year, month)
    val ranking = FinanceStats.merchantRanking(entries, year, month, 8)
    val daily = FinanceStats.dailyTotals(entries, year, month)
    val (fixed, variable) = FinanceStats.fixedVsVariable(entries, year, month, fixedCategories)
    val confirmedMonth = FinanceStats.confirmed(entries).filter { FinanceStats.inMonth(it.occurredAt, year, month) }
    val avgTicket = if (cur.count > 0) cur.expenseCents / cur.count else 0L

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        contentPadding = FabSafeBottomPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Text("统计分析", style = MaterialTheme.typography.headlineMedium)
            Text("只基于已确认账单 · 点击图表可查看对应流水", style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        item {
            AppCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            val c = Calendar.getInstance().apply { set(year, month - 1, 1); add(Calendar.MONTH, -1) }
                            year = c.get(Calendar.YEAR); month = c.get(Calendar.MONTH) + 1
                        }) { Text("‹ 上月") }
                        Text("$year 年 $month 月", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            val c = Calendar.getInstance().apply { set(year, month - 1, 1); add(Calendar.MONTH, 1) }
                            year = c.get(Calendar.YEAR); month = c.get(Calendar.MONTH) + 1
                        }) { Text("下月 ›") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(6, 12).forEach { n ->
                            FilterChip(selected = monthsBack == n, onClick = { monthsBack = n }, label = { Text("近 $n 月") })
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onExport) { Text("导出 CSV") }
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("月度收支对比", "点击柱子查看该月流水")
                    Spacer(Modifier.height(10.dp))
                    GroupedBarChart(
                        labels = series.map { it.label },
                        incomes = series.map { it.incomeCents },
                        expenses = series.map { it.expenseCents },
                        onSelect = { i ->
                            series.getOrNull(i)?.let { s -> year = s.year; month = s.month }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("收入 ¥${money(cur.incomeCents)}", style = MaterialTheme.typography.bodySmall)
                        Text("支出 ¥${money(cur.expenseCents)}", style = MaterialTheme.typography.bodySmall)
                        val r = FinanceStats.changeRate(cur.expenseCents, prev.expenseCents)
                        Text(if (r == null) "环比：无可比数据" else "环比 ${if (r >= 0) "+" else ""}${(r * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("消费趋势", "最近 $monthsBack 个月净支出")
                    Spacer(Modifier.height(10.dp))
                    TrendLineChart(
                        values = series.map { it.expenseCents },
                        labels = series.map { it.label },
                        onSelect = { i ->
                            series.getOrNull(i)?.let { s -> year = s.year; month = s.month }
                        }
                    )
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("分类消费占比", "点击扇区筛选分类")
                    Spacer(Modifier.height(8.dp))
                    if (categoryTotals.isEmpty()) EmptyState("暂无支出数据", "确认账单后自动生成")
                    else {
                        val slices = categoryTotals.mapIndexed { i, (name, value) ->
                            DonutSlice(name, value, ChartPalette.forIndex(i))
                        }
                        DonutChart(
                            slices = slices,
                            centerTitle = "本月支出",
                            centerValue = "¥${money(cur.expenseCents)}",
                            onSelect = { i -> selectedCategory = categoryTotals.getOrNull(i)?.first }
                        )
                        Spacer(Modifier.height(10.dp))
                        categoryTotals.take(6).forEachIndexed { i, (name, value) ->
                            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("●", color = ChartPalette.forIndex(i), style = MaterialTheme.typography.bodySmall)
                                Text(" $name", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text("¥${money(value)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("每日消费热力图", "点击某天查看当天流水")
                    Spacer(Modifier.height(10.dp))
                    val days = Calendar.getInstance().apply { set(year, month - 1, 1) }
                        .getActualMaximum(Calendar.DAY_OF_MONTH)
                    CalendarHeatMap(daily, days, onSelect = { selectedDay = it })
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("商户消费排行", "点击某一行筛选商户")
                    Spacer(Modifier.height(8.dp))
                    if (ranking.isEmpty()) EmptyState("暂无商户数据", "确认账单后自动生成")
                    else RankBars(ranking, onSelect = { i -> selectedMerchant = ranking.getOrNull(i)?.first })
                    Spacer(Modifier.height(8.dp))
                    Text("平均单笔 ¥${money(avgTicket)} · 已确认 ${cur.count} 笔", style = MaterialTheme.typography.bodySmall)
                    Text("固定支出 ¥${money(fixed)} / 非固定 ¥${money(variable)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    val pAmount = pending.filter { it.type == PaymentType.EXPENSE }.sumOf { it.amountCents }
                    SectionTitle("待确认账单（不计入以上统计）", "${pending.size} 笔 · ¥${money(pAmount)}")
                    Text("确认后才会进入图表与汇总，避免重复计算。", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    val filters = listOfNotNull(
                        selectedCategory?.let { "分类：$it" },
                        selectedMerchant?.let { "商户：$it" },
                        selectedDay?.let { "$month 月 $it 日" }
                    )
                    SectionTitle(
                        if (filters.isEmpty()) "本月已确认流水" else "筛选结果",
                        if (filters.isEmpty()) "点击上方图表可筛选" else filters.joinToString(" · "),
                        action = {
                            if (filters.isNotEmpty()) TextButton(onClick = {
                                selectedCategory = null; selectedMerchant = null; selectedDay = null
                            }) { Text("清除") }
                        }
                    )
                    val shown = confirmedMonth.filter { e ->
                        (selectedCategory == null || e.category == selectedCategory) &&
                            (selectedMerchant == null || e.merchant == selectedMerchant) &&
                            (selectedDay == null || Calendar.getInstance().apply { timeInMillis = e.occurredAt }
                                .get(Calendar.DAY_OF_MONTH) == selectedDay)
                    }
                    if (shown.isEmpty()) EmptyState("没有匹配的流水", "换个筛选条件试试")
                    else shown.take(20).forEach { EntryRow(it) { onEntryClick(it) } }
                }
            }
        }
    }
}
