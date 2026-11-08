package site.zhizhang.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryCompletenessTest {

    @Test
    fun `wechat deduction voucher with only amount lacks all three fields`() {
        val c = EntryCompleteness.check("微信支付", "未分类", "")
        assertTrue(c.needsDetail)
        assertTrue(c.missingMerchant)
        assertTrue(c.missingCategory)
        assertTrue(c.missingItem)
        assertEquals("缺商户 / 分类 / 买了什么", c.summary)
    }

    @Test
    fun `placeholder merchant like pdd order is still incomplete`() {
        val c = EntryCompleteness.check("拼多多订单", "购物消费", "")
        assertTrue(c.missingMerchant)
        assertFalse(c.missingCategory)
        assertTrue(c.missingItem)
    }

    @Test
    fun `user filled merchant category and item is complete`() {
        val c = EntryCompleteness.check("拼多多·日用品店", "购物消费", "抽纸两提")
        assertFalse(c.needsDetail)
        assertEquals("", c.summary)
    }

    @Test
    fun `note alone does not fix a missing category`() {
        val c = EntryCompleteness.check("楼下便利店", "未分类", "矿泉水")
        assertTrue(c.needsDetail)
        assertFalse(c.missingMerchant)
        assertFalse(c.missingItem)
        assertTrue(c.missingCategory)
    }

    @Test
    fun `blank merchant is treated as generic`() {
        assertTrue(EntryCompleteness.isGenericMerchant(""))
        assertTrue(EntryCompleteness.isGenericMerchant("  "))
        assertTrue(EntryCompleteness.isGenericMerchant("支付宝"))
        assertFalse(EntryCompleteness.isGenericMerchant("瑞幸咖啡（科技园店）"))
    }
}
