package site.zhizhang.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import site.zhizhang.app.core.ParseConfidence
import site.zhizhang.app.core.PaymentType
import site.zhizhang.app.data.ClassificationSource
import site.zhizhang.app.data.EntryStatus
import site.zhizhang.app.data.LedgerEntry

class DataCodecTest {

    private fun entry(id: Long, key: String, merchant: String = "测试商户") = LedgerEntry(
        id = id, amountCents = 1234, type = PaymentType.EXPENSE, merchant = merchant,
        category = "餐饮美食", sourcePackage = "manual", sourceName = "手动",
        occurredAt = 1_700_000_000_000L, note = "备注", rawNotification = "原始通知",
        status = EntryStatus.CONFIRMED, confidence = ParseConfidence.HIGH,
        classificationSource = ClassificationSource.MANUAL, needsAi = false, dedupKey = key
    )

    @Test
    fun `yuan to cents is exact`() {
        assertEquals(1234L, DataCodec.yuanToCents("12.34"))
        assertEquals(123456L, DataCodec.yuanToCents("¥1,234.56"))
        assertEquals(-1L, DataCodec.yuanToCents("abc"))
    }

    @Test
    fun `csv header matching and invalid row skipping`() {
        val csv = "日期,金额,商户,分类,类型,来源,备注\n" +
            "2026-11-01,12.34,瑞幸咖啡,餐饮美食,支出,微信,拿铁\n" +
            "坏行,不是金额,,,,\n" +
            "2026-11-02,8.00,地铁出行,交通出行,支出,支付宝,\n"
        val r = DataCodec.parseCsv(csv)
        assertEquals(2, r.rows.size)
        assertEquals(1, r.skipped)
        assertEquals(1234L, r.rows[0].amountCents)
        assertEquals("瑞幸咖啡", r.rows[0].merchant)
        assertTrue(r.rows[1].occurredAt > 0)
    }

    @Test
    fun `csv quoted comma is preserved`() {
        val csv = "日期,金额,商户,分类\n2026-11-01,12.34,\"商户,分店A\",餐饮美食\n"
        val r = DataCodec.parseCsv(csv)
        assertEquals("商户,分店A", r.rows.single().merchant)
    }

    @Test
    fun `json backup round trip keeps special characters`() {
        val tricky = "商户\"引号\"{花括号}\n换行"
        val list = listOf(entry(1, "k1", tricky), entry(2, "k2"))
        val json = DataCodec.toBackupJson(list)
        val restored = DataCodec.fromBackupJson(json)
        assertEquals(2, restored.size)
        assertEquals(tricky, restored[0].merchant)
        assertEquals(1234L, restored[1].amountCents)
        assertEquals("餐饮美食", restored[0].category)
    }

    @Test
    fun `duplicate detection ignores blank keys`() {
        val list = listOf(entry(1, "same"), entry(2, "same"), entry(3, ""), entry(4, ""))
        assertEquals(listOf(2L), DataCodec.duplicateIds(list))
    }
}
