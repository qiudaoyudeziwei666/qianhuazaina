package site.zhizhang.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryEngineTest {

    @Test
    fun `user rule wins over built-in keyword`() {
        val d = CategoryEngine.classify(
            merchant = "瑞幸咖啡",
            userRules = listOf("瑞幸咖啡" to "通讯订阅")
        )
        assertEquals("通讯订阅", d.category)
        assertEquals(95, d.confidence)
        assertEquals("USER_RULE", d.source)
    }

    @Test
    fun `longer user rule wins over shorter one`() {
        val d = CategoryEngine.classify(
            merchant = "美团外卖订单",
            userRules = listOf("美团" to "购物消费", "美团外卖" to "餐饮美食")
        )
        assertEquals("餐饮美食", d.category)
        assertEquals("美团外卖", d.matchedBy)
    }

    @Test
    fun `built-in keyword used when no user rule matches`() {
        val d = CategoryEngine.classify("瑞幸咖啡（科技园店）")
        assertEquals("餐饮美食", d.category)
        assertEquals(80, d.confidence)
        assertFalse(d.needsAi)
    }

    @Test
    fun `history infers category for unknown merchant`() {
        val d = CategoryEngine.classify(
            merchant = "街角小铺",
            history = listOf("街角小铺" to "购物消费", "街角小铺" to "购物消费", "街角小铺" to "餐饮美食")
        )
        assertEquals("购物消费", d.category)
        assertEquals(60, d.confidence)
        assertEquals("HISTORY", d.source)
    }

    @Test
    fun `unknown merchant needs AI and has zero confidence`() {
        val d = CategoryEngine.classify("微信支付", "已扣费¥11.54")
        assertEquals("未分类", d.category)
        assertEquals(0, d.confidence)
        assertTrue(d.needsAi)
        assertEquals("NONE", d.source)
    }

    @Test
    fun `income flow uses income categories`() {
        val d = CategoryEngine.classify("工资", isIncome = true)
        assertEquals("工资收入", d.category)
        assertFalse(d.needsAi)
    }
}
