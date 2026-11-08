package site.zhizhang.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenerHealthTest {

    private val now = 1_000_000_000_000L

    @Test
    fun `never received a notification means it should rebind`() {
        assertTrue(ListenerHealth.shouldRebind(0L, now))
        assertTrue(ListenerHealth.shouldRebind(-1L, now))
    }

    @Test
    fun `recent notification is healthy`() {
        assertFalse(ListenerHealth.shouldRebind(now - 60_000L, now))
        assertFalse(ListenerHealth.shouldRebind(now - 29 * 60_000L, now))
    }

    @Test
    fun `silent for over thirty minutes should rebind`() {
        assertTrue(ListenerHealth.shouldRebind(now - 31 * 60_000L, now))
    }

    @Test
    fun `exactly at the threshold is still healthy`() {
        assertFalse(ListenerHealth.shouldRebind(now - ListenerHealth.STALE_AFTER_MS, now))
    }
}
