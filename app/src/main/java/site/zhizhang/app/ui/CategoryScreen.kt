package site.zhizhang.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import site.zhizhang.app.core.CategoryClassifier
import site.zhizhang.app.core.CategoryEngine
import site.zhizhang.app.data.CategoryRuleEntity
import site.zhizhang.app.data.ClassificationHistoryEntity
import site.zhizhang.app.data.LedgerEntry

/**
 * 智能分类：分层优先级（用户规则 > 本地关键词 > 历史 > AI），
 * 含置信度、批量分类、修改历史、常用商户规则管理与本地调用统计。
 */
@Composable
fun CategoryScreen(
    entries: List<LedgerEntry>,
    needsAi: List<LedgerEntry>,
    classifying: Boolean,
    rules: List<CategoryRuleEntity>,
    history: List<ClassificationHistoryEntity>,
    aiCallCount: Int,
    aiTokens: Long,
    onClassify: (List<LedgerEntry>) -> Unit,
    onLocalClassify: (List<LedgerEntry>) -> Unit,
    onAddRule: (String, String) -> Unit,
    onDeleteRule: (CategoryRuleEntity) -> Unit,
    onClearHistory: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var selectedIds by remember(needsAi) { mutableStateOf(needsAi.map { it.id }.toSet()) }
    var showAddRule by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    var ruleCategory by remember { mutableStateOf("餐饮美食") }
    val rulePairs = rules.map { it.keyword to it.category }
    val localCount = entries.count { it.classificationSource.name == "LOCAL" && !it.needsAi }

    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = FabSafeBottomPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Text("智能分类", style = MaterialTheme.typography.headlineMedium)
            Text("用户规则 → 本地关键词 → 历史记录 → 才考虑 AI", style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        item {
            Column(Modifier.fillMaxWidth().background(Mint, RoundedCornerShape(22.dp)).padding(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, null, tint = Green)
                    Text("  本地优先", style = MaterialTheme.typography.titleMedium, color = Green)
                }
                Text("大部分分类无需联网", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 16.dp))
                Text("你的手动修改会学成商户规则；只有本地无法确定且你主动批量整理时才调用 API。",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                Row(modifier = Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column { Text("$localCount 笔", style = MaterialTheme.typography.titleLarge); Text("本地已分类", style = MaterialTheme.typography.bodySmall) }
                    Column { Text("$aiCallCount 次", style = MaterialTheme.typography.titleLarge); Text("AI 批量调用", style = MaterialTheme.typography.bodySmall) }
                    Column { Text("$aiTokens", style = MaterialTheme.typography.titleLarge); Text("估算 token", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("待分类流水", "勾选后合并处理，每批最多 20 笔", action = {
                        TextButton(onClick = onOpenSettings) { Text("配置 API") }
                    })
                    if (needsAi.isEmpty()) EmptyState("没有待分类流水", "本地规则已处理当前可识别的交易")
                    needsAi.forEach { entry ->
                        val decision = CategoryEngine.classify(
                            merchant = entry.merchant, rawText = entry.rawNotification,
                            userRules = rulePairs
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                            Checkbox(checked = entry.id in selectedIds, onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + entry.id else selectedIds - entry.id
                            })
                            Column(Modifier.weight(1f)) {
                                Text(entry.merchant, style = MaterialTheme.typography.bodyMedium)
                                Text("${entry.sourceName} · ¥${money(entry.amountCents)} · 置信度 ${decision.confidence}%",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                            Text("待识别", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (needsAi.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { onLocalClassify(needsAi.filter { it.id in selectedIds }) },
                            enabled = selectedIds.isNotEmpty(), modifier = Modifier.fillMaxWidth()
                        ) { Text("本地重分类（不联网）") }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { onClassify(needsAi.filter { it.id in selectedIds }.take(20)) },
                            enabled = !classifying && selectedIds.isNotEmpty(), modifier = Modifier.fillMaxWidth()
                        ) {
                            if (classifying) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.AutoAwesome, null)
                            Text(if (classifying) "  正在批量整理…" else "  批量智能分类（1 次请求）")
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("常用商户规则", "命中后优先于内置关键词", action = {
                        TextButton(onClick = { showAddRule = true }) { Text("＋ 添加") }
                    })
                    Spacer(Modifier.height(8.dp))
                    if (rules.isEmpty()) Text("还没有自定义规则。修改流水分类时会自动学习。", style = MaterialTheme.typography.bodySmall)
                    rules.forEach { rule ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(rule.keyword, style = MaterialTheme.typography.bodyMedium)
                                Text("→ ${rule.category} · 命中 ${rule.hits} 次", style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { onDeleteRule(rule) }) { Text("删除") }
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("分类修改历史", "最近 50 条，仅存本机", action = {
                        TextButton(onClick = onClearHistory) { Text("清空") }
                    })
                    Spacer(Modifier.height(8.dp))
                    if (history.isEmpty()) EmptyState("暂无修改记录", "手动改分类或批量分类后会出现在这里")
                    history.take(5).forEach { h ->
                        Text("· ${h.oldCategory} → ${h.newCategory}（${h.source}）",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 3.dp))
                    }
                }
            }
        }
        item {
            AppCard {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = Green)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("最小化发送数据", style = MaterialTheme.typography.titleSmall)
                        Text("默认仅发送商户名和收支类型；金额默认脱敏。API Key 使用 Android Keystore 加密保存。",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(76.dp)) }
    }

    if (showAddRule) {
        AlertDialog(
            onDismissRequest = { showAddRule = false },
            title = { Text("添加商户规则") },
            text = {
                Column {
                    OutlinedTextField(value = keyword, onValueChange = { keyword = it },
                        label = { Text("商户关键词") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Text("分类：$ruleCategory", style = MaterialTheme.typography.bodySmall)
                    CategoryClassifier.defaultCategories.filterNot { it.contains("收入") || it == "退款" }
                        .take(6).forEach { c ->
                            TextButton(onClick = { ruleCategory = c }) { Text(c) }
                        }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (keyword.isNotBlank()) { onAddRule(keyword, ruleCategory); keyword = ""; showAddRule = false }
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showAddRule = false }) { Text("取消") } }
        )
    }
}
