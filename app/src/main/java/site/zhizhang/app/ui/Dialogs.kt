package site.zhizhang.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import site.zhizhang.app.core.CategoryClassifier
import site.zhizhang.app.data.EntryStatus
import site.zhizhang.app.data.LedgerEntry

@Composable
fun AddManualDialog(onDismiss: () -> Unit, onSave: (Long, String, String, String, String) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("餐饮美食") }
    var source by remember { mutableStateOf("微信支付") }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("记一笔") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 560.dp)) {
                OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' }.take(12) },
                    label = { Text("金额（元）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("商户 / 备注") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true)
                Text("分类", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 6.dp)) {
                    items(CategoryClassifier.defaultCategories.filterNot { it.contains("收入") || it == "退款" }.size) { index ->
                        val item = CategoryClassifier.defaultCategories.filterNot { it.contains("收入") || it == "退款" }[index]
                        FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                    }
                }
                Text("支付方式", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf("微信支付", "支付宝", "现金", "银行卡").forEach { item ->
                        item { FilterChip(selected = source == item, onClick = { source = item }, label = { Text(item) }) }
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            }
        },
        confirmButton = {
            Button(onClick = {
                val cents = amount.toBigDecimalOrNull()?.movePointRight(2)?.toLong() ?: 0L
                onSave(cents, merchant, category, source, note)
            }) { Text("保存流水") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun EditEntryDialog(
    entry: LedgerEntry,
    suggestions: List<Pair<String, String>> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (LedgerEntry) -> Unit,
    onDelete: (LedgerEntry) -> Unit
) {
    var merchant by remember(entry.id) { mutableStateOf(entry.merchant) }
    var category by remember(entry.id) { mutableStateOf(entry.category) }
    var note by remember(entry.id) { mutableStateOf(entry.note) }
    var showRaw by remember(entry.id) { mutableStateOf(false) }
    val quickCategories = CategoryClassifier.defaultCategories
        .filterNot { it.contains("收入") || it == "退款" || it == "未分类" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("补全这笔流水") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).heightIn(max = 560.dp)) {
                Text("${if (entry.type.name == "INCOME") "+" else "-"}¥${money(entry.amountCents)}", style = MaterialTheme.typography.headlineMedium)
                Text("${entry.sourceName} · ${formatTime(entry.occurredAt)}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "支付凭证通知通常只有金额，商户和买了什么并不在通知里。补一次，下面可从最近记录一键套用。",
                    style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedTextField(value = merchant, onValueChange = { merchant = it }, label = { Text("在哪儿花的（商户）") },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
                if (suggestions.isNotEmpty()) {
                    Text("最近记过的商户（点一下带出分类）", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 4.dp)) {
                        suggestions.forEach { (name, cat) ->
                            item {
                                FilterChip(
                                    selected = merchant == name,
                                    onClick = {
                                        merchant = name
                                        if (category.isBlank() || category == "未分类") category = cat
                                    },
                                    label = { Text(name) }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("分类") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp), singleLine = true)
                Text("常用分类", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(top = 4.dp)) {
                    quickCategories.forEach { name ->
                        item { FilterChip(selected = category == name, onClick = { category = name }, label = { Text(name) }) }
                    }
                }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("买了什么 / 备注") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
                if (entry.rawNotification.isNotBlank()) {
                    TextButton(onClick = { showRaw = !showRaw }) { Text(if (showRaw) "收起原始通知" else "查看原始通知") }
                    if (showRaw) Text(entry.rawNotification, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(entry.copy(merchant = merchant.ifBlank { entry.merchant }, category = category.ifBlank { "未分类" }, note = note)) }) {
                Text("保存")
            }
        },
        dismissButton = {
            Column {
                if (entry.status == EntryStatus.PENDING) TextButton(onClick = { onSave(entry.copy(status = EntryStatus.CONFIRMED)) }) { Text("确认入账") }
                TextButton(onClick = { onDelete(entry) }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
