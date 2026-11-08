package site.zhizhang.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentParserTest {
    @Test
    fun `parses WeChat expense with merchant and category`() {
        val result = PaymentParser.parse(
            "com.tencent.mm", "微信支付", "你已成功支付 ¥35.50\n商户：肯德基（科技园店）", 1000L
        )!!
        assertEquals(3550L, result.amountCents)
        assertEquals(PaymentType.EXPENSE, result.type)
        assertEquals("肯德基（科技园店）", result.merchant)
        assertEquals("餐饮美食", result.category)
        assertEquals(ParseConfidence.HIGH, result.confidence)
        assertFalse(result.needsAi)
    }

    @Test
    fun `parses Alipay expense with yuan sign`() {
        val result = PaymentParser.parse(
            "com.eg.android.AlipayGphone", "支付宝", "付款成功 ￥26.80\n收款方：奈雪的茶（海岸城店）", 1000L
        )!!
        assertEquals(2680L, result.amountCents)
        assertEquals("奈雪的茶（海岸城店）", result.merchant)
        assertEquals("餐饮美食", result.category)
    }

    @Test
    fun `shopping order without merchant stays reviewable`() {
        val result = PaymentParser.parse(
            "com.taobao.taobao", "淘宝", "订单支付成功，实付款 ¥129.00", 1000L
        )!!
        assertEquals(12900L, result.amountCents)
        assertEquals("淘宝订单", result.merchant)
        assertEquals("购物消费", result.category)
        assertEquals(ParseConfidence.LOW, result.confidence)
        assertTrue(result.needsAi)
    }

    @Test
    fun `refund is income rather than expense`() {
        val result = PaymentParser.parse(
            "com.jingdong.app.mall", "京东", "退款成功 ¥89.90\n商户：京东自营", 1000L
        )!!
        assertEquals(PaymentType.INCOME, result.type)
        assertEquals("退款", result.category)
        assertEquals(8990L, result.amountCents)
    }

    @Test
    fun `marketing and non payment notifications are ignored`() {
        assertNull(PaymentParser.parse("com.tencent.mm", "微信支付", "支付有礼，领取优惠券", 1000L))
        assertNull(PaymentParser.parse("com.taobao.taobao", "淘宝", "你关注的商品降价了", 1000L))
        assertNull(PaymentParser.parse("com.tencent.mm", "微信支付", "待付款订单 ¥99.00", 1000L))
        assertNull(PaymentParser.parse("android", "系统", "支付成功 ¥10.00", 1000L))
    }

    @Test
    fun `WeChat transfer to a friend is recognised`() {
        val result = PaymentParser.parse(
            "com.tencent.mm", "微信支付", "你已转账 ¥120.00 给 张三", 1000L
        )!!
        assertEquals(12000L, result.amountCents)
        assertEquals(PaymentType.EXPENSE, result.type)
        assertTrue(result.merchant.contains("张三") || result.merchant == "微信支付")
    }

    @Test
    fun `Pinduoduo order payment is recognised`() {
        val result = PaymentParser.parse(
            "com.xunmeng.pinduoduo", "拼多多", "订单支付成功，实付款 ¥29.90", 1000L
        )!!
        assertEquals(2990L, result.amountCents)
        assertEquals("购物消费", result.category)
        assertEquals("拼多多订单", result.merchant)
    }

    @Test
    fun `order paid with shipping status words is not discarded`() {
        val result = PaymentParser.parse(
            "com.xunmeng.pinduoduo", "拼多多", "订单支付成功 ¥18.80，商家待发货", 1000L
        )!!
        assertEquals(1880L, result.amountCents)
    }

    @Test
    fun `real Xiaomi WeChat Pay summary notification is recognised`() {
        // 真机抓到的原文：微信支付公众号把多条消息折叠成一条摘要通知
        val result = PaymentParser.parse(
            "com.tencent.mm", "微信支付", "[2条]微信支付: 已扣费¥5.98", 1000L
        )!!
        assertEquals(598L, result.amountCents)
        assertEquals(PaymentType.EXPENSE, result.type)
        assertEquals("微信支付", result.merchant)
        assertEquals(ParseConfidence.LOW, result.confidence)
    }

    @Test
    fun `auto deduction wording is recognised`() {
        val result = PaymentParser.parse(
            "com.eg.android.AlipayGphone", "支付宝", "免密支付已扣费 ￥18.00", 1000L
        )!!
        assertEquals(1800L, result.amountCents)
    }

    @Test
    fun `transfer receipt without amount is ignored`() {
        assertNull(PaymentParser.parse("com.tencent.mm", "张统", "[转账] 已被接收", 1000L))
    }

    @Test
    fun `cancelled order notification is ignored`() {
        assertNull(PaymentParser.parse("com.tencent.mm", "微信支付", "「支付分先用后付」订单已取消", 1000L))
    }

    @Test
    fun `same notification has stable dedup key`() {
        val first = PaymentParser.parse("com.tencent.mm", "微信支付", "支付成功 ¥8.00\n商户：深圳地铁", 1000L)!!
        val second = PaymentParser.parse("com.tencent.mm", "微信支付", "支付成功 ¥8.00\n商户：深圳地铁", 2000L)!!
        assertEquals(first.dedupKey, second.dedupKey)
        assertNotEquals(first.dedupKey, "manual-1")
    }
}
