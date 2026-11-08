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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import site.zhizhang.app.core.FinanceStats
import site.zhizhang.app.data.CategoryBudgetEntity
import site.zhizhang.app.data.LedgerEntry
import site.zhizhang.app.data.SavingsGoalEntity
import site.zhizhang.app.data.SubscriptionEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

@Composable
fun PageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        TextButton(onClick = onBack) { Text("‹ 返回") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun yuanToCents(text: String): Long = runCatching {
    (text.trim().toBigDecimal().movePointRight(2).toLong())
}.getOrDefault(0L)

private fun dateLabel(ts: Long): String =
    if (ts <= 0) "未设置" else SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(ts))

// ---------------- 预算 ----------------
@Composable
fun BudgetScreen(
    entries: List<LedgerEntry>,
    monthlyBudgetCents: Long,
    budgets: List<CategoryBudgetEntity>,
    fixedCategories: Set<String>,
    onSetMonthly: (Long) -> Unit,
    onUpsert: (String, Long) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    val d = FinanceStats.buildDashboard(entries, monthlyBudgetCents)
    var editingTotal by remember { mutableStateOf(false) }
    var totalText by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }
    var newLimit by remember { mutableStateOf("") }
    val cats = FinanceStats.categoryTotals(entries, d.month.year, d.month.month)
    val categories = (budgets.map { it.category } + cats.map { it.first } + listOf("餐饮美食", "购物消费", "交通出行", "居家生活", "休闲娱乐", "通讯订阅"))
        .distinct()

    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = FabSafeBottomPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("预算管理", "基于已确认账单，不使用虚构消费", onBack) }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("月度总预算", "达到 85% 时高亮提醒", action = {
                        TextButton(onClick = { totalText = "%,.2f".format(monthlyBudgetCents / 100.0); editingTotal = true }) { Text("修改") }
                    })
                    Spacer(Modifier.height(10.dp))
                    Text("¥${money(monthlyBudgetCents)}", style = MaterialTheme.typography.titleLarge)
                    val used = if (monthlyBudgetCents > 0)
                        (d.month.expenseCents.toDouble() / monthlyBudgetCents).coerceIn(0.0, 1.0) else 0.0
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(10.dp).background(Line, RoundedCornerShape(5.dp))) {
                        Box(Modifier.fillMaxWidth(used.toFloat()).height(10.dp)
                            .background(if (used >= 0.85) Danger else Green, RoundedCornerShape(5.dp)))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("已用 ¥${money(d.month.expenseCents)} · 剩余 ¥${money(max(d.budgetRemainingCents, 0L))}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("按当前速度，月末预计 ¥${money(d.forecastCents)}" +
                        if (d.forecastCents > monthlyBudgetCents && monthlyBudgetCents > 0)
                            "（预计超支 ¥${money(d.forecastCents - monthlyBudgetCents)}）" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (d.forecastCents > monthlyBudgetCents && monthlyBudgetCents > 0) Danger else Muted)
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("分类预算", "为每个分类设定月度额度")
                    Spacer(Modifier.height(8.dp))
                    budgets.forEach { b ->
                        val spent = cats.firstOrNull { it.first == b.category }?.second ?: 0L
                        val pct = if (b.limitCents > 0) (spent.toDouble() / b.limitCents).coerceIn(0.0, 1.0) else 0.0
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(b.category, style = MaterialTheme.typography.titleSmall)
                                Text("¥${money(spent)} / ¥${money(b.limitCents)}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.fillMaxWidth().height(6.dp).background(Line, RoundedCornerShape(3.dp))) {
                                    Box(Modifier.fillMaxWidth(pct.toFloat()).height(6.dp)
                                        .background(if (pct >= 0.85) Danger else Green, RoundedCornerShape(3.dp)))
                                }
                            }
                            TextButton(onClick = { onDelete(b.category) }) { Text("删除") }
                        }
                    }
                    if (budgets.isEmpty()) Text("尚未设置分类预算", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        var expanded by remember { mutableStateOf(false) }
                        Column(Modifier.weight(1f)) {
                            TextButton(onClick = { expanded = !expanded }) { Text(newCategory.ifBlank { "选择分类" }) }
                            if (expanded) categories.forEach { c ->
                                TextButton(onClick = { newCategory = c; expanded = false }) { Text(c) }
                            }
                        }
                        OutlinedTextField(value = newLimit, onValueChange = { newLimit = it },
                            label = { Text("额度(元)") }, modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    Button(onClick = {
                        if (newCategory.isNotBlank() && yuanToCents(newLimit) > 0) {
                            onUpsert(newCategory, yuanToCents(newLimit)); newCategory = ""; newLimit = ""
                        }
                    }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("保存分类预算") }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    val (fixed, variable) = FinanceStats.fixedVsVariable(entries, d.month.year, d.month.month, fixedCategories)
                    SectionTitle("支出结构", "固定 / 非固定")
                    Text("固定 ¥${money(fixed)} · 非固定 ¥${money(variable)}", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    if (editingTotal) {
        AlertDialog(
            onDismissRequest = { editingTotal = false }, title = { Text("修改月度总预算") },
            text = {
                OutlinedTextField(value = totalText, onValueChange = { totalText = it }, label = { Text("金额（元）") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            },
            confirmButton = {
                Button(onClick = {
                    val cents = yuanToCents(totalText)
                    if (cents > 0) { onSetMonthly(cents); editingTotal = false }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingTotal = false }) { Text("取消") } }
        )
    }
}

// ---------------- 储蓄目标 ----------------
@Composable
fun GoalsScreen(
    goals: List<SavingsGoalEntity>,
    entries: List<LedgerEntry>,
    onAdd: (String, Long, Long, Long, String) -> Unit,
    onUpdate: (SavingsGoalEntity) -> Unit,
    onDelete: (SavingsGoalEntity) -> Unit,
    onBack: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    // 月建议储蓄 = 最近 3 个月平均结余（为负则按 0 处理，不虚构存款能力）
    val recent = FinanceStats.series(entries, 3)
    val avgMonthlyBalance = if (recent.isEmpty()) 0L else max(recent.sumOf { it.balanceCents } / recent.size, 0L)

    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = FabSafeBottomPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("储蓄目标", "已存金额由你手动登记，不等于账户余额或预算结余", onBack) }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    Text("近 3 个月平均结余", style = MaterialTheme.typography.titleSmall)
                    Text("¥${money(avgMonthlyBalance)}", style = MaterialTheme.typography.titleLarge)
                    Text("以此作为「每月建议储蓄」的参考值", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        goals.forEach { goal ->
            item(key = goal.id) {
                AppCard {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(goal.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onUpdate(goal.copy(savedCents = goal.savedCents + 10_000)) }) { Text("+¥100") }
                            TextButton(onClick = { onDelete(goal) }) { Text("删除") }
                        }
                        val pct = if (goal.targetCents > 0) (goal.savedCents.toDouble() / goal.targetCents).coerceIn(0.0, 1.0) else 0.0
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().height(8.dp).background(Line, RoundedCornerShape(4.dp))) {
                            Box(Modifier.fillMaxWidth(pct.toFloat()).height(8.dp).background(Green, RoundedCornerShape(4.dp)))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("已存 ¥${money(goal.savedCents)} / 目标 ¥${money(goal.targetCents)} · ${(pct * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall)
                        Text("期限：${dateLabel(goal.deadlineAt)}", style = MaterialTheme.typography.bodySmall)
                        val remain = max(goal.targetCents - goal.savedCents, 0L)
                        val months = FinanceStats.monthsToGoal(remain, avgMonthlyBalance)
                        Text(
                            if (months == null) "按当前结余速度暂无法推算达成时间" else "按当前速度还需约 $months 个月 · 每月建议存 ¥${money(remain / max(months, 1))}",
                            style = MaterialTheme.typography.bodySmall, color = Muted
                        )
                        if (goal.note.isNotBlank()) Text(goal.note, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item {
            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ 新建储蓄目标") }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false }, title = { Text("新建储蓄目标") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("目标名称") }, singleLine = true)
                    OutlinedTextField(value = target, onValueChange = { target = it }, label = { Text("目标金额（元）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = saved, onValueChange = { saved = it }, label = { Text("已存金额（元）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注（可选）") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val t = yuanToCents(target); val s = yuanToCents(saved)
                    if (name.isNotBlank() && t > 0) {
                        val deadline = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }.timeInMillis
                        onAdd(name, t, s, deadline, note)
                        name = ""; target = ""; saved = ""; note = ""; showAdd = false
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
        )
    }
}

// ---------------- 订阅管理 ----------------
@Composable
fun SubscriptionsScreen(
    subs: List<SubscriptionEntity>,
    onAdd: (String, Long, String, Long, String) -> Unit,
    onUpdate: (SubscriptionEntity) -> Unit,
    onDelete: (SubscriptionEntity) -> Unit,
    onBack: () -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var cycle by remember { mutableStateOf("MONTHLY") }
    var note by remember { mutableStateOf("") }
    val annual = subs.filter { it.active }.sumOf { FinanceStats.annualSubscriptionCost(it.amountCents, it.cycle) }

    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = FabSafeBottomPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("订阅管理", "会员 / 软件 / 云服务的周期性扣费", onBack) }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    Text("年度订阅总成本", style = MaterialTheme.typography.titleSmall)
                    Text("¥${money(annual)}", style = MaterialTheme.typography.titleLarge)
                    Text("折合每月 ¥${money(annual / 12)} · 仅统计启用中的订阅", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        subs.forEach { sub ->
            item(key = sub.id) {
                AppCard {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(sub.name, style = MaterialTheme.typography.titleSmall)
                                Text("${cycleLabel(sub.cycle)} ¥${money(sub.amountCents)} · 年化 ¥${money(
                                    FinanceStats.annualSubscriptionCost(sub.amountCents, sub.cycle))}",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = sub.active, onCheckedChange = { onUpdate(sub.copy(active = it)) })
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("下次扣费：${dateLabel(sub.nextDueAt)}", style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = {
                                val next = Calendar.getInstance().apply {
                                    timeInMillis = max(sub.nextDueAt, System.currentTimeMillis())
                                    add(Calendar.MONTH, when (sub.cycle) { "QUARTERLY" -> 3; "YEARLY" -> 12; else -> 1 })
                                }.timeInMillis
                                onUpdate(sub.copy(nextDueAt = next, lastUsedAt = System.currentTimeMillis()))
                            }) { Text("标记已使用 / 顺延") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { onDelete(sub) }) { Text("删除") }
                        }
                        if (sub.note.isNotBlank()) Text(sub.note, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ 添加订阅") } }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false }, title = { Text("添加订阅") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
                    OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("每期金额（元）") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("MONTHLY" to "月付", "QUARTERLY" to "季付", "YEARLY" to "年付").forEach { (key, label) ->
                            FilterChip(selected = cycle == key, onClick = { cycle = key }, label = { Text(label) })
                        }
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注（可选）") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val a = yuanToCents(amount)
                    if (name.isNotBlank() && a > 0) {
                        val next = Calendar.getInstance().apply { add(Calendar.MONTH, 1) }.timeInMillis
                        onAdd(name, a, cycle, next, note)
                        name = ""; amount = ""; note = ""; showAdd = false
                    }
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
        )
    }
}

private fun cycleLabel(cycle: String) = when (cycle.uppercase()) {
    "QUARTERLY" -> "季付"; "YEARLY" -> "年付"; else -> "月付"
}

// ---------------- 消费洞察 ----------------
@Composable
fun InsightsScreen(
    entries: List<LedgerEntry>,
    monthlyBudgetCents: Long,
    fixedCategories: Set<String>,
    nonEssentialCategories: Set<String>,
    onBack: () -> Unit
) {
    val lines = FinanceStats.insightLines(entries, monthlyBudgetCents, fixedCategories, nonEssentialCategories)
    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = FabSafeBottomPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("消费洞察", "确定性统计规则生成，不调用 AI、不编造数据", onBack) }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("本月的观察", "基于已确认账单")
                    Spacer(Modifier.height(8.dp))
                    lines.forEach { line ->
                        Text("· $line", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp))
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("口径说明", "避免重复计算")
                    Text("只统计已确认账单；退款冲减支出；转账不计入收支；待确认流水单独显示。",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
