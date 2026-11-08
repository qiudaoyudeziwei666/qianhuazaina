package site.zhizhang.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCardsTest {

    @Test
    fun `blank config yields defaults with all cards visible`() {
        val list = HomeCards.parse("")
        assertEquals(HomeCards.ids.size, list.size)
        assertTrue(list.all { it.second })
    }

    @Test
    fun `saved order wins and missing ids are appended`() {
        val list = HomeCards.parse("RECENT:1,SUMMARY:0")
        assertEquals("RECENT", list[0].first)
        assertEquals("SUMMARY", list[1].first)
        assertFalse(list[1].second)
        assertEquals(HomeCards.ids.size, list.size)
    }

    @Test
    fun `unknown ids are ignored`() {
        val list = HomeCards.parse("BOGUS:1,TREND:1")
        assertTrue(list.none { it.first == "BOGUS" })
        assertEquals("TREND", list[0].first)
    }

    @Test
    fun `serialize and parse round trip`() {
        val raw = HomeCards.serialize(HomeCards.defaults())
        assertEquals(HomeCards.defaults(), HomeCards.parse(raw))
    }

    @Test
    fun `move swaps adjacent cards and respects bounds`() {
        val list = HomeCards.defaults()
        val moved = HomeCards.move(list, 1, true)
        assertEquals(list[1].first, moved[0].first)
        assertEquals(list, HomeCards.move(list, 0, true))
    }

    @Test
    fun `toggle flips visibility and visible returns only enabled`() {
        val toggled = HomeCards.toggle(HomeCards.defaults(), 0)
        assertFalse(toggled[0].second)
        assertEquals(HomeCards.ids.size - 1, HomeCards.visible(toggled).size)
    }
}
