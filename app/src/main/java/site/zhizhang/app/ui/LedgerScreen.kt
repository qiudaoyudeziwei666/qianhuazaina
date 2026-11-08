package site.zhizhang.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import site.zhizhang.app.data.EntryStatus
import site.zhizhang.app.data.LedgerEntry
import site.zhizhang.app.core.PaymentType
import java.util.Calendar

/** 账单页：组合筛选（关键词 / 类型 / 分类 / 时间范围）+ 批量编辑与删除，使用 LazyColumn 控制性能 */
@Composable
fun LedgerScreen(
    entries: List<LedgerEntry>,
    onEntryClick: (LedgerEntry) -> Unit,
    onBatchCategory: (List<LedgerEntry>, String) -> Unit,
    onBatchDelete: (List<LedgerEntry>) -> Unit,
    onUndo: () -> Unit,
    now: Long = System.currentTimeMillis()
) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf("全部") }
    var categoryFilter by remember { mutableStateOf("全部分类") }
    var rangeFilter by remember { mutableStateOf("全部") }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val categories = (listOf("全部分类") + entries.map { it.category }.distinct().sorted()).take(12)
    val monthStart = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val threeMonthsAgo = Calendar.getInstance().apply {
        timeInMillis = monthStart; add(Calendar.MONTH, -2)
    }.timeInMillis

    val filtered = entries.filter { e ->
        val matchesQuery = query.isBlank() || listOf(
            e.merchant, e.category, e.sourceName, e.note, money(e.amountCents)
        ).any { it.contains(query, true) }
        val matchesType = when (typeFilter) {
            "支出" -> e.type == PaymentType.EXPENSE || e.type == PaymentType.REFUND
            "收入" -> e.type == PaymentType.INCOME
            "待确认" -> e.status == EntryStatus.PENDING
            "转账" -> e.type == PaymentType.TRANSFER
            else -> true
        }
        val matchesCategory = categoryFilter == "全部分类" || e.category == categoryFilter
        val matchesRange = when (rangeFilter) {
            "本月" -> e.occurredAt >= monthStart
            "近 3 月" -> e.occurredAt >= threeMonthsAgo
            else -> true
        }
        matchesQuery && matchesType && matchesCategory && matchesRange
    }
    val chosen = filtered.filter { it.id in selectedIds }

    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        contentPadding = FabSafeBottomPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            Text("全部流水", style = MaterialTheme.typography.headlineMedium)
            Text("组合筛选 · 批量编辑 · 结果 ${filtered.size} 笔", style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) },
                placeholder = { Text("搜索商户、分类、金额或备注") }
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "支出", "收入", "待确认", "转账").forEach { label ->
                    item { FilterChip(selected = typeFilter == label, onClick = { typeFilter = label }, label = { Text(label) }) }
                }
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "本月", "近 3 月").forEach { label ->
                    item { FilterChip(selected = rangeFilter == label, onClick = { rangeFilter = label }, label = { Text(label) }) }
                }
                categories.forEach { c ->
                    item { FilterChip(selected = categoryFilter == c, onClick = { categoryFilter = c }, label = { Text(c) }) }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    selectedIds = if (selectedIds.size == filtered.size) emptySet() else filtered.map { it.id }.toSet()
                }) { Text(if (selectedIds.size == filtered.size && filtered.isNotEmpty()) "清空选择" else "全选结果") }
                Text("已选 ${selectedIds.size} 笔", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onUndo) { Text("撤销删除") }
            }
        }
        if (selectedIds.isNotEmpty()) {
            item {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onBatchDelete(chosen); selectedIds = emptySet() }, modifier = Modifier.weight(1f)) {
                            Text("批量删除")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("餐饮美食", "购物消费", "交通出行", "居家生活", "其他").forEach { c ->
                            TextButton(onClick = { onBatchCategory(chosen, c); selectedIds = emptySet() }) { Text(c) }
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    if (filtered.isEmpty()) EmptyState("没有匹配的流水", "换个关键词或筛选条件试试")
                    else filtered.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = e.id in selectedIds, onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + e.id else selectedIds - e.id
                            })
                            Column(Modifier.weight(1f)) { EntryRow(e) { onEntryClick(e) } }
                        }
                    }
                }
            }
        }
    }
}
