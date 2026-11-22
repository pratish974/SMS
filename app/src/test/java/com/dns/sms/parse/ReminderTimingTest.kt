package com.dns.sms.parse

import com.dns.sms.reminders.ReminderTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTimingTest {
    private val h = 3_600_000L

    @Test fun leadTimes() {
        assertEquals(100 * h - 24 * h, ReminderTiming.notifyAt("BILL", 100 * h))
        assertEquals(100 * h - 2 * h, ReminderTiming.notifyAt("FLIGHT", 100 * h))
        assertEquals(100 * h, ReminderTiming.notifyAt("MANUAL", 100 * h))
    }

    @Test fun nextAndDue() {
        val items = listOf("MANUAL" to 10 * h, "FLIGHT" to 20 * h, "BILL" to 50 * h)
        assertEquals(10 * h, ReminderTiming.next(items, 0))
        assertEquals(18 * h, ReminderTiming.next(items, 10 * h))
        assertNull(ReminderTiming.next(items, 30 * h))
        val due = ReminderTiming.due(items, { it.first }, { it.second }, lastFired = 5 * h, now = 19 * h)
        assertEquals(listOf("MANUAL", "FLIGHT"), due.map { it.first })
        // Never fires twice: the next window starts where this one ended.
        assertEquals(emptyList<Pair<String, Long>>(), ReminderTiming.due(items, { it.first }, { it.second }, 19 * h, 19 * h))
    }
}
