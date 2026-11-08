package site.zhizhang.app.util

import android.content.Context
import android.net.Uri
import site.zhizhang.app.data.LedgerEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    fun export(context: Context, uri: Uri, entries: List<LedgerEntry>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        val content = buildString {
            append("时间,类型,商户,金额,分类,支付方式,状态,备注\n")
            entries.forEach { entry ->
                val fields = listOf(
                    dateFormat.format(Date(entry.occurredAt)),
                    if (entry.type.name == "INCOME") "收入" else "支出",
                    entry.merchant,
                    "%.2f".format(entry.amountCents / 100.0),
                    entry.category,
                    entry.sourceName,
                    entry.status.name,
                    entry.note
                )
                append(fields.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }).append('\n')
            }
        }
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write("\ufeff")
            it.write(content)
        } ?: error("无法写入所选文件")
    }
}
