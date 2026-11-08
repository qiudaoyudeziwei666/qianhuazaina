package site.zhizhang.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.zhizhang.app.core.ActivityStatus
import site.zhizhang.app.core.FinanceStats
import site.zhizhang.app.core.HomeCards
import site.zhizhang.app.core.PaymentType
import site.zhizhang.app.data.EntryStatus
import site.zhizhang.app.data.LedgerEntry
import site.zhizhang.app.data.SavingsGoalEntity
import kotlin.math.max

data class HomeActions(
    val onOpenAuto: () -> Unit,
    val onOpenLedger: () -> Unit,
    val onOpenStats: () -> Unit,
    val onOpenBudget: () -> Unit,
    val onOpenGoals: () -> Unit,
    val onOpenSubs: () -> Unit,
    val onOpenInsights: () -> Unit,
    val onOpenData: () -> Unit,
    val onAdd: () -> Unit,
    val onEntryClick: (LedgerEntry) -> Unit,
    val onCardsChange: (List<Pair<String, Boolean>>) -> Unit
)

@Composable
fun HomeScreen(
    entries: List<LedgerEntry>,
    notificationEnabled: Boolean,
    lastNotificationAt: Long,
    dashboard: FinanceStats.Dashboard,
    goals: List<SavingsGoalEntity>,
    insights: List<String>,
    cardConfig: List<Pair<String, Boolean>>,
    actions: HomeActions
) {
    var showConfig by remember { mutableStateOf(false) }
    val confirmed = entries.filter { it.status == EntryStatus.CONFIRMED }
    val pendingEntries = entries.filter { it.status == EntryStatus.PENDING }
    val pendingAmount = pendingEntries.filter { it.type == PaymentType.EXPENSE }.sumOf { it.amountCents }
    val visible = HomeCards.visible(cardConfig)

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        contentPadding = FabSafeBottomPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("钱花在哪", style = MaterialTheme.typography.headlineMedium)
                    Text("自动记录 → 智能分类 → 可视化分析", style = MaterialTheme.typography.bodyMedium, color = Muted,
                        modifier = Modifier.padding(top = 6.dp))
                }
                TextButton(onClick = { showConfig = true }) { Text("自定义") }
            }
        }
        visible.forEach { id ->
            item(key = id) {
                when (id) {
                    "SUMMARY" -> SummaryCard(dashboard)
                    "QUICK" -> QuickCard(actions)
                    "PENDING" -> if (pendingEntries.isNotEmpty()) PendingCard(pendingEntries.size, pendingAmount, actions.onOpenAuto)
                    "BUDGET" -> BudgetCard(dashboard, actions.onOpenBudget)
                    "TREND" -> TrendCard(dashboard)
                    "SAVINGS" -> SavingsCard(goals.firstOrNull(), actions.onOpenGoals)
                    "INSIGHTS" -> InsightsCard(insights, actions.onOpenInsights)
                    "AUTO" -> AutoCard(notificationEnabled, lastNotificationAt, actions.onOpenAuto)
                    "RECENT" -> RecentCard(confirmed, actions.onOpenLedger, actions.onEntryClick)
                }
            }
        }
        item { Spacer(Modifier.height(76.dp)) }
    }

    if (showConfig) {
        CardConfigDialog(cardConfig, onDismiss = { showConfig = false }) { updated ->
            actions.onCardsChange(updated)
        }
    }
}

@Composable
private fun SummaryCard(d: FinanceStats.Dashboard) {
    val m = d.month
    Box(Modifier.fillMaxWidth().background(Mint, RoundedCornerShape(24.dp)).padding(22.dp)) {
        Column {
            Text("${m.month} 月 · 已确认支出", color = Muted, style = MaterialTheme.typography.bodyMedium)
            Text("¥ ${money(m.expenseCents)}", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Ink,
                modifier = Modifier.padding(vertical = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column { Text("本月收入", style = MaterialTheme.typography.bodySmall); Text("¥ ${money(m.incomeCents)}", fontWeight = FontWeight.SemiBold) }
                Column { Text("本月结余", style = MaterialTheme.typography.bodySmall); Text("¥ ${money(m.balanceCents)}", fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                val expChange = d.expenseChange
                Text(
                    if (expChange == null) "上月无可比数据" else "支出环比 ${if (expChange >= 0) "+" else ""}${(expChange * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall, color = Green
                )
                Text("日均 ¥${money(d.dailyAverageCents)}", style = MaterialTheme.typography.bodySmall, color = Green)
            }
            Text("预算剩余 ¥${money(max(d.budgetRemainingCents, 0L))}", style = MaterialTheme.typography.bodySmall,
                color = Muted, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun QuickCard(a: HomeActions) {
    AppCard {
        Column(Modifier.padding(16.dp)) {
            SectionTitle("常用功能", "把最频繁的操作放在前面")
            Spacer(Modifier.height(12.dp))
            // 3 列布局下保持短标签，避免文字被截断
            val items = listOf(
                "记一笔" to a.onAdd,
                "分析" to a.onOpenStats,
                "预算" to a.onOpenBudget,
                "储蓄" to a.onOpenGoals,
                "订阅" to a.onOpenSubs,
                "数据" to a.onOpenData
            )
            items.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, action) ->
                        Button(onClick = action, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Green)) {
                            Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PendingCard(count: Int, amount: Long, onOpen: () -> Unit) {
    AppCard {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, null, tint = Amber)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("$count 笔待确认 · 合计 ¥${money(amount)}", style = MaterialTheme.typography.titleSmall)
                Text("确认后才计入统计，待确认账单单独显示", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onOpen) { Text("去确认") }
        }
    }
}

@Composable
private fun BudgetCard(d: FinanceStats.Dashboard, onOpen: () -> Unit) {
    AppCard {
        Column(Modifier.padding(18.dp)) {
            SectionTitle("预算健康度", "根据已确认账单实时计算", action = { TextButton(onClick = onOpen) { Text("管理") } })
            Spacer(Modifier.height(12.dp))
            val used = if (d.budgetCents > 0) (d.month.expenseCents.toDouble() / d.budgetCents).coerceIn(0.0, 1.0) else 0.0
            // 两行左对齐：右侧留给悬浮「＋」按钮，避免关键文字被遮挡
            Text("${(used * 100).toInt()}% 已使用", style = MaterialTheme.typography.titleSmall)
            Text("月末预计 ¥${money(d.forecastCents)}", style = MaterialTheme.typography.bodySmall, color = Muted)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(10.dp).background(Line, RoundedCornerShape(5.dp))) {
                Box(Modifier.fillMaxWidth(used.toFloat()).height(10.dp).background(
                    if (used > 0.85) Danger else Green, RoundedCornerShape(5.dp)
                ))
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已用 ¥${money(d.month.expenseCents)}", style = MaterialTheme.typography.bodySmall)
                Text("剩余 ¥${money(max(d.budgetRemainingCents, 0L))}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TrendCard(d: FinanceStats.Dashboard) {
    AppCard {
        Column(Modifier.padding(18.dp)) {
            SectionTitle("近期消费趋势", "本月每日净支出")
            Spacer(Modifier.height(8.dp))
            if (d.trendCents.isEmpty() || d.trendCents.all { it == 0L }) EmptyState("暂无趋势数据", "确认几笔流水后自动生成")
            else MiniTrendChart(d.trendCents)
        }
    }
}

@Composable
private fun SavingsCard(goal: SavingsGoalEntity?, onOpen: () -> Unit) {
    AppCard {
        Column(Modifier.padding(18.dp)) {
            SectionTitle("储蓄进度", "手动登记的已存金额，不等于账户余额", action = { TextButton(onClick = onOpen) { Text("管理") } })
            Spacer(Modifier.height(12.dp))
            if (goal == null) {
                EmptyState("还没有储蓄目标", "点「管理」创建一个")
            } else {
                val pct = if (goal.targetCents > 0) (goal.savedCents.toDouble() / goal.targetCents).coerceIn(0.0, 1.0) else 0.0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(goal.name, style = MaterialTheme.typography.titleSmall)
                        Text("¥${money(goal.savedCents)} / ¥${money(goal.targetCents)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${(pct * 100).toInt()}%", style = MaterialTheme.typography.titleSmall, color = Green)
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).background(Line, RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxWidth(pct.toFloat()).height(8.dp).background(Green, RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}

@Composable
private fun InsightsCard(lines: List<String>, onOpen: () -> Unit) {
    AppCard {
        Column(Modifier.padding(18.dp)) {
            SectionTitle("消费洞察", "由确定性统计规则生成", action = { TextButton(onClick = onOpen) { Text("全部") } })
            Spacer(Modifier.height(8.dp))
            lines.take(2).forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
        }
    }
}

@Composable
private fun AutoCard(enabled: Boolean, lastAt: Long, onOpen: () -> Unit) {
    AppCard {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(Mint, RoundedCornerShape(12.dp)).padding(10.dp)) {
                Icon(Icons.Default.Bolt, null, tint = Green)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(if (enabled) "自动记账运行中" else "自动记账尚未授权", style = MaterialTheme.typography.titleSmall)
                Text(ActivityStatus.describe(enabled, lastAt), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onOpen) { Icon(Icons.Default.ChevronRight, null, tint = Muted) }
        }
    }
}

@Composable
private fun RecentCard(entries: List<LedgerEntry>, onOpenAll: () -> Unit, onEntry: (LedgerEntry) -> Unit) {
    AppCard {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Box(Modifier.padding(top = 8.dp)) {
                SectionTitle("最近账单", "只展示已确认交易", action = {
                    TextButton(onClick = onOpenAll, modifier = Modifier.padding(end = 68.dp)) { Text("查看全部") }
                })
            }
            if (entries.isEmpty()) EmptyState("还没有流水", "可手动记一笔，或在自动记账页测试通知解析")
            else entries.take(5).forEach { EntryRow(it) { onEntry(it) } }
        }
    }
}

@Composable
private fun CardConfigDialog(
    config: List<Pair<String, Boolean>>,
    onDismiss: () -> Unit,
    onChange: (List<Pair<String, Boolean>>) -> Unit
) {
    var list by remember(config) { mutableStateOf(config) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义首页") },
        text = {
            LazyColumn {
                list.forEachIndexed { index, (id, shown) ->
                    item(key = id) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(HomeCards.labels[id] ?: id, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { list = HomeCards.move(list, index, true) }) { Text("↑") }
                            TextButton(onClick = { list = HomeCards.move(list, index, false) }) { Text("↓") }
                            Switch(checked = shown, onCheckedChange = { list = HomeCards.toggle(list, index) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onChange(list); onDismiss() }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
