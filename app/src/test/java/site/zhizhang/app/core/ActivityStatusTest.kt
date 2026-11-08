package site.zhizhang.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ActivityStatusTest {

    private fun todayAt(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `disabled listener asks for permission`() {
        assertEquals("开启通知访问后才会自动识别", ActivityStatus.describe(false, 0L))
    }

    @Test
    fun `enabled but never received a notification`() {
        assertEquals("已授权，尚未收到过任何通知", ActivityStatus.describe(true, 0L))
    }

    @Test
    fun `same day notification shows only clock time`() {
        val now = todayAt(20, 0)
        val result = ActivityStatus.describe(true, todayAt(10, 30), now)
        assertTrue(result, result.contains("上次收到通知 10:30"))
        assertTrue(result, result.contains("退出界面仍在后台"))
    }

    @Test
    fun `older notification includes the date`() {
        val now = todayAt(20, 0)
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        val result = ActivityStatus.describe(true, yesterday, now)
        assertTrue(result, result.contains("月") && result.contains("日"))
    }
}
