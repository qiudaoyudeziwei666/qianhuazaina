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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import site.zhizhang.app.core.EntryCompleteness
import site.zhizhang.app.core.SupportedApps
import site.zhizhang.app.data.LedgerEntry
import site.zhizhang.app.util.NotificationLog
import site.zhizhang.app.util.PermissionHelper

@Composable
fun AutoScreen(
    pending: List<LedgerEntry>,
    notificationEnabled: Boolean,
    autoConfirm: Boolean,
    onAutoConfirmChange: (Boolean) -> Unit,
    onConfirm: (LedgerEntry) -> Unit,
    onIgnore: (LedgerEntry) -> Unit,
    onTest: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onEdit: (LedgerEntry) -> Unit,
    onConfirmAll: (List<LedgerEntry>) -> Unit,
    onIgnoreAll: (List<LedgerEntry>) -> Unit,
    onBatchCategory: (List<LedgerEntry>, String) -> Unit
) {
    val context = LocalContext.current
    var showTest by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var selectedIds by remember(pending) { mutableStateOf(emptySet<Long>()) }
    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = FabSafeBottomPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Text("自动记账", style = MaterialTheme.typography.headlineMedium)
            Text("支付通知在本机识别，不读取聊天正文", style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        item {
            Box(Modifier.fillMaxWidth().background(Mint, RoundedCornerShape(22.dp)).padding(20.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.background(AppSurface, RoundedCornerShape(14.dp)).padding(12.dp)) {
                            Icon(Icons.Default.Bolt, null, tint = Green)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(if (notificationEnabled) "正在默默为你记账" else "需要通知访问权限", style = MaterialTheme.typography.titleMedium)
                            Text(if (notificationEnabled) "服务已连接 · 重复通知自动去重" else "系统授权后才能读取支付通知", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (!PermissionHelper.enableListenerAfterAdbGrant(context)) PermissionHelper.openNotificationSettings(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Green)
                    ) {
                        Icon(Icons.Default.Notifications, null)
                        Spacer(Modifier.padding(4.dp))
                        Text(if (notificationEnabled) "检查系统授权" else "开启通知访问")
                    }
                }
            }
        }
        item {
            AppCard {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("高置信度自动入账", style = MaterialTheme.typography.titleSmall)
                        Text("商户与金额明确时直接确认；其余进入待确认", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = autoConfirm, onCheckedChange = onAutoConfirmChange)
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("待确认流水", "${pending.size} 笔 · 确认后参与统计", action = {
                        TextButton(onClick = {
                            selectedIds = if (selectedIds.size == pending.size) emptySet()
                            else pending.map { it.id }.toSet()
                        }) { Text(if (selectedIds.size == pending.size) "清空" else "全选") }
                    })
                    if (selectedIds.isNotEmpty()) {
                        val chosen = pending.filter { it.id in selectedIds }
                        Text("已选 ${selectedIds.size} 笔 · 批量操作不调用 API", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onConfirmAll(chosen); selectedIds = emptySet() }, modifier = Modifier.weight(1f)) {
                                Text("批量确认")
                            }
                            OutlinedButton(onClick = { onIgnoreAll(chosen); selectedIds = emptySet() }, modifier = Modifier.weight(1f)) {
                                Text("批量忽略")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("餐饮美食", "购物消费", "交通出行", "居家生活").forEach { c ->
                                TextButton(onClick = { onBatchCategory(chosen, c); selectedIds = emptySet() }) { Text(c) }
                            }
                        }
                    }
                    if (pending.isEmpty()) EmptyState("没有待确认流水", "高置信度自动入账或低置信度待你核对")
                    pending.forEach { entry ->
                        Column(Modifier.padding(vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = entry.id in selectedIds, onCheckedChange = { checked ->
                                    selectedIds = if (checked) selectedIds + entry.id else selectedIds - entry.id
                                })
                                Box(Modifier.weight(1f)) { EntryRow(entry) {} }
                            }
                            val gap = EntryCompleteness.check(entry.merchant, entry.category, entry.note)
                            if (gap.needsDetail) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${gap.summary} · 扣费通知里只有金额",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Amber,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { onEdit(entry) }) { Text("补全信息") }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { onConfirm(entry) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Check, null); Text(" 确认入账")
                                }
                                OutlinedButton(onClick = { onIgnore(entry) }) { Text("忽略") }
                            }
                        }
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("通知解析测试", "不实际付款，也能验证识别规则")
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { showTest = true }, modifier = Modifier.fillMaxWidth()) { Text("粘贴通知文本测试") }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("通知诊断", "看收到过什么、为什么没入账")
                    Text("只记录含支付/订单/金额字样的通知，明文仅存本机，不上传。",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { showDiagnostics = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("查看最近收到的通知")
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("已适配的通知来源", "按包名白名单过滤，不处理其他应用")
                    SupportedApps.sources.forEach { source ->
                        Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            SourceIcon(source.displayName)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(source.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text("支付 / 扣款 / 退款（以实际通知为准）", style = MaterialTheme.typography.bodySmall)
                            }
                            Text("已适配", color = Green, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        item {
            TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("查看 ADB 授权与隐私说明"); Icon(Icons.Default.ChevronRight, null)
            }
            Spacer(Modifier.height(76.dp))
        }
    }
    if (showTest) TestNotificationDialog(onDismiss = { showTest = false }, onTest = { onTest(it); showTest = false })
    if (showDiagnostics) {
        val logs = remember { NotificationLog.recent(context, 30) }
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = { Text("最近收到的通知（${logs.size} 条）") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (logs.isEmpty()) {
                        Text("还没有记录到交易类通知。\n\n如果刚付完款仍为空，说明通知没有送到知账——多半是被系统折叠、或由厂商推送代发导致包名不在白名单。",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    logs.forEach { item ->
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text("${item.time} · ${item.pkg}", style = MaterialTheme.typography.labelSmall, color = Muted)
                            Text("${item.title} / ${item.text}", style = MaterialTheme.typography.bodySmall)
                            Text(
                                (if (item.parsed) "✅ 已解析 ¥%.2f".format(item.amount / 100.0) else "⏭ 未入账") + " · ${item.reason}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.parsed) Green else Amber
                            )
                            androidx.compose.material3.HorizontalDivider(Modifier.padding(top = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDiagnostics = false }) { Text("关闭") } },
            dismissButton = {
                TextButton(onClick = { NotificationLog.clear(context); showDiagnostics = false }) { Text("清空记录") }
            }
        )
    }
}

@Composable
private fun TestNotificationDialog(onDismiss: () -> Unit, onTest: (String) -> Unit) {
    var text by remember { mutableStateOf("你已成功支付 ¥35.50\n商户：肯德基（科技园店）") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("测试支付通知") },
        text = {
            Column {
                Text("解析完全在本机进行，支持粘贴微信、支付宝或购物订单通知。测试按微信格式处理。")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 4, label = { Text("通知文本") })
            }
        },
        confirmButton = { Button(onClick = { onTest(text) }) { Text("解析并加入待确认") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
