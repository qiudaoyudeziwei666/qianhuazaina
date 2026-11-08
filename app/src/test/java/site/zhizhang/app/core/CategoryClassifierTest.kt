package site.zhizhang.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryClassifierTest {
    @Test
    fun `local rules recognize common merchants`() {
        assertEquals("餐饮美食", CategoryClassifier.classify("瑞幸咖啡").category)
        assertEquals("交通出行", CategoryClassifier.classify("深圳地铁").category)
        assertEquals("居家生活", CategoryClassifier.classify("山姆会员商店").category)
        assertEquals("医疗健康", CategoryClassifier.classify("海王星辰药房").category)
    }

    @Test
    fun `unknown merchant needs manual batch classification`() {
        val result = CategoryClassifier.classify("SZ-TECH*8921")
        assertEquals("未分类", result.category)
        assertTrue(result.needsAi)
    }

    @Test
    fun `known specific merchant does not need AI`() {
        assertFalse(CategoryClassifier.classify("瑞幸咖啡").needsAi)
    }
}
