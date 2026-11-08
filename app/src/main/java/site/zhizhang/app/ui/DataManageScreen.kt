package site.zhizhang.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import site.zhizhang.app.util.DataCodec

/** 数据管理：导入预览、完整备份、追加式恢复、去重、撤销删除、完整性校验 */
@Composable
fun DataManageScreen(
    entryCount: Int,
    preview: DataCodec.CsvResult?,
    onDismissPreview: () -> Unit,
    onConfirmImport: () -> Unit,
    onExportCsv: () -> Unit,
    onImportPick: () -> Unit,
    onBackupPick: () -> Unit,
    onRestorePick: () -> Unit,
    onDedup: () -> Unit,
    onUndo: () -> Unit,
    onValidate: () -> Unit,
    onBack: () -> Unit
) {
    var confirmRestore by remember { mutableStateOf(false) }
    var confirmDedup by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.padding(horizontal = 16.dp), contentPadding = FabSafeBottomPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("数据管理", "当前 $entryCount 笔可见流水 · 所有操作只在本机", onBack) }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("导出与备份")
                    Text("CSV 适合表格查看；JSON 备份保留分类、来源、去重键等全部元数据。",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onExportCsv, modifier = Modifier.weight(1f)) { Text("导出 CSV") }
                        Button(onClick = onBackupPick, modifier = Modifier.weight(1f)) { Text("JSON 备份") }
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("导入与恢复", "导入前先看预览，恢复采用追加模式")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onImportPick, modifier = Modifier.weight(1f)) { Text("导入 CSV") }
                        OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.weight(1f)) { Text("恢复备份") }
                    }
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("数据维护")
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onValidate, modifier = Modifier.weight(1f)) { Text("完整性校验") }
                        OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f)) { Text("撤销删除") }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { confirmDedup = true }, modifier = Modifier.fillMaxWidth()) { Text("清理重复流水（需确认）") }
                    Text("重复判定：相同去重键只保留第一笔。清理后不可整体恢复，请谨慎。",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            AppCard {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("口径说明")
                    Text("统计与图表只使用已确认账单；待确认单独显示；退款冲减支出；转账不计入收支。恢复备份会追加新记录，不会覆盖现有账本。",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    if (preview != null) {
        AlertDialog(
            onDismissRequest = onDismissPreview,
            title = { Text("导入预览") },
            text = {
                Column {
                    Text("可解析 ${preview.rows.size} 笔 · 跳过 ${preview.skipped} 行", style = MaterialTheme.typography.titleSmall)
                    Text("识别到的字段：${preview.headers.joinToString(" / ").take(120)}",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(8.dp))
                    preview.rows.take(4).forEach { r ->
                        Text("· ${r.merchant} ¥${money(r.amountCents)} · ${r.category}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("确认后会写入账本，重复去重键自动跳过。", style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { Button(onClick = onConfirmImport) { Text("确认导入") } },
            dismissButton = { TextButton(onClick = onDismissPreview) { Text("取消") } }
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("恢复备份") },
            text = { Text("恢复采用追加模式：只会新增记录，绝不覆盖或清空现有账本。请选择之前导出的 JSON 备份文件。") },
            confirmButton = {
                Button(onClick = { confirmRestore = false; onRestorePick() }) { Text("选择文件") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("取消") } }
        )
    }

    if (confirmDedup) {
        AlertDialog(
            onDismissRequest = { confirmDedup = false },
            title = { Text("确认清理重复流水？") },
            text = { Text("这是破坏性操作：相同去重键的重复记录会被删除，且无法整体撤销。建议先做一次 JSON 备份。") },
            confirmButton = {
                Button(onClick = { confirmDedup = false; onDedup() }) { Text("确认清理") }
            },
            dismissButton = { TextButton(onClick = { confirmDedup = false }) { Text("取消") } }
        )
    }
}
