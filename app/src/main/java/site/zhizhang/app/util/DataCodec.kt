package site.zhizhang.app.util

import site.zhizhang.app.core.ParseConfidence
import site.zhizhang.app.core.PaymentType
import site.zhizhang.app.data.ClassificationSource
import site.zhizhang.app.data.EntryStatus
import site.zhizhang.app.data.LedgerEntry
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 导入 / 备份的纯函数编解码（零第三方依赖，可 JVM 单测）。
 * 金额一律用「分」的 Long，禁止 Double 参与汇总；字符串字段做完整转义。
 */
object DataCodec {

    data class ImportRow(
        val occurredAt: Long,
        val amountCents: Long,
        val merchant: String,
        val category: String,
        val type: PaymentType,
        val sourceName: String,
        val note: String
    )

    data class CsvResult(val rows: List<ImportRow>, val skipped: Int, val headers: List<String>)

    private val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA),
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA),
        SimpleDateFormat("yyyy/MM/dd", Locale.CHINA)
    )

    fun yuanToCents(raw: String): Long = runCatching {
        raw.trim().replace("¥", "").replace("￥", "").replace(",", "")
            .toBigDecimal().movePointRight(2).toLong()
    }.getOrDefault(-1L)

    private fun parseDate(raw: String): Long {
        val s = raw.trim()
        for (f in dateFormats) {
            runCatching { return f.parse(s)?.time ?: 0L }
        }
        return 0L
    }

    private fun splitCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out.map { it.trim() }
    }

    private fun columnIndex(headers: List<String>, vararg names: String): Int {
        headers.forEachIndexed { i, h ->
            if (names.any { h.contains(it, ignoreCase = true) }) return i
        }
        return -1
    }

    /** 支持表头字段匹配；无法解析金额或日期的行跳过并计数，不静默造数据 */
    fun parseCsv(text: String): CsvResult {
        val lines = text.split("\n").map { it.trim('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return CsvResult(emptyList(), 0, emptyList())
        val headers = splitCsvLine(lines.first())
        val iDate = columnIndex(headers, "日期", "时间", "date")
        val iAmount = columnIndex(headers, "金额", "amount")
        val iMerchant = columnIndex(headers, "商户", "名称", "交易对方", "merchant")
        val iCategory = columnIndex(headers, "分类", "category")
        val iType = columnIndex(headers, "类型", "收支", "type")
        val iSource = columnIndex(headers, "来源", "支付方式", "source")
        val iNote = columnIndex(headers, "备注", "商品", "note")
        val rows = mutableListOf<ImportRow>()
        var skipped = 0
        for (line in lines.drop(1)) {
            val cells = splitCsvLine(line)
            val amount = if (iAmount >= 0) yuanToCents(cells.getOrNull(iAmount).orEmpty()) else -1L
            val ts = if (iDate >= 0) parseDate(cells.getOrNull(iDate).orEmpty()) else 0L
            if (amount <= 0 || ts <= 0L) { skipped++; continue }
            val typeRaw = cells.getOrNull(iType).orEmpty()
            val type = if (typeRaw.contains("收") || typeRaw.equals("INCOME", true)) PaymentType.INCOME
                else if (typeRaw.contains("退")) PaymentType.REFUND
                else if (typeRaw.contains("转")) PaymentType.TRANSFER
                else PaymentType.EXPENSE
            rows += ImportRow(
                occurredAt = ts,
                amountCents = amount,
                merchant = cells.getOrNull(iMerchant).orEmpty().ifBlank { "导入账单" },
                category = cells.getOrNull(iCategory).orEmpty().ifBlank { "未分类" },
                type = type,
                sourceName = cells.getOrNull(iSource).orEmpty().ifBlank { "导入" },
                note = cells.getOrNull(iNote).orEmpty()
            )
        }
        return CsvResult(rows, skipped, headers)
    }

    fun importDedupKey(row: ImportRow): String =
        "import-${row.occurredAt}-${row.amountCents}-${row.merchant}"

    fun rowToEntry(row: ImportRow): LedgerEntry = LedgerEntry(
        amountCents = row.amountCents,
        type = row.type,
        merchant = row.merchant,
        category = row.category,
        sourcePackage = "import",
        sourceName = row.sourceName,
        occurredAt = row.occurredAt,
        note = row.note,
        status = EntryStatus.CONFIRMED,
        confidence = ParseConfidence.HIGH,
        classificationSource = ClassificationSource.MANUAL,
        needsAi = row.category == "未分类",
        dedupKey = importDedupKey(row)
    )

    // ---------- JSON 完整备份（NDJSON：每行一个流水对象，第一行是元信息） ----------
    private fun esc(s: String): String = buildString {
        s.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '{' -> append("\\u007B")
                '}' -> append("\\u007D")
                else -> append(c)
            }
        }
    }

    private fun unesc(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                    '"' -> sb.append('"'); '\\' -> sb.append('\\')
                    'u' -> {
                        if (i + 5 < s.length && s.substring(i + 2, i + 6) == "007B") { sb.append('{'); i += 4 }
                        else if (i + 5 < s.length && s.substring(i + 2, i + 6) == "007D") { sb.append('}'); i += 4 }
                        else sb.append('?')
                    }
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    fun toBackupJson(entries: List<LedgerEntry>): String = buildString {
        append("{\"kind\":\"meta\",\"version\":2,\"count\":${entries.size}}\n")
        entries.forEach { e ->
            append("{\"kind\":\"entry\",\"id\":${e.id},\"amountCents\":${e.amountCents},")
            append("\"type\":\"${e.type.name}\",\"merchant\":\"${esc(e.merchant)}\",")
            append("\"category\":\"${esc(e.category)}\",\"sourcePackage\":\"${esc(e.sourcePackage)}\",")
            append("\"sourceName\":\"${esc(e.sourceName)}\",\"occurredAt\":${e.occurredAt},")
            append("\"createdAt\":${e.createdAt},\"note\":\"${esc(e.note)}\",")
            append("\"rawNotification\":\"${esc(e.rawNotification)}\",\"status\":\"${e.status.name}\",")
            append("\"confidence\":\"${e.confidence.name}\",\"classificationSource\":\"${e.classificationSource.name}\",")
            append("\"needsAi\":${e.needsAi},\"dedupKey\":\"${esc(e.dedupKey)}\"}\n")
        }
    }

    private fun field(obj: String, name: String): String? {
        val r = Regex("\"" + name + "\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\"|[^,}\\s]+)")
        val m = r.find(obj) ?: return null
        val raw = m.groupValues[1]
        return if (raw.startsWith("\"")) unesc(raw.removeSurrounding("\"")) else raw
    }

    fun fromBackupJson(text: String): List<LedgerEntry> {
        val out = mutableListOf<LedgerEntry>()
        text.lines().filter { it.contains("\"kind\":\"entry\"") }.forEach { line ->
            runCatching {
                val f = { n: String -> field(line, n) }
                out += LedgerEntry(
                    id = f("id")?.toLongOrNull() ?: 0L,
                    amountCents = f("amountCents")?.toLongOrNull() ?: return@forEach,
                    type = PaymentType.valueOf(f("type") ?: "EXPENSE"),
                    merchant = f("merchant").orEmpty(),
                    category = f("category") ?: "未分类",
                    sourcePackage = f("sourcePackage") ?: "import",
                    sourceName = f("sourceName") ?: "导入",
                    occurredAt = f("occurredAt")?.toLongOrNull() ?: return@forEach,
                    createdAt = f("createdAt")?.toLongOrNull() ?: System.currentTimeMillis(),
                    note = f("note").orEmpty(),
                    rawNotification = f("rawNotification").orEmpty(),
                    status = EntryStatus.valueOf(f("status") ?: "CONFIRMED"),
                    confidence = ParseConfidence.valueOf(f("confidence") ?: "HIGH"),
                    classificationSource = ClassificationSource.valueOf(
                        runCatching { f("classificationSource") ?: "LOCAL" }.getOrDefault("LOCAL")
                    ),
                    needsAi = f("needsAi") == "true",
                    dedupKey = f("dedupKey").orEmpty()
                )
            }
        }
        return out
    }

    /** 按 dedupKey 找重复（返回应删除的条目 ID），空 key 不参与 */
    fun duplicateIds(entries: List<LedgerEntry>): List<Long> {
        val seen = mutableSetOf<String>()
        return entries.filter { it.dedupKey.isNotBlank() && !seen.add(it.dedupKey) }.map { it.id }
    }
}
