package com.dns.sms.ui.conversation

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {
    private val ny = ZoneId.of("America/New_York")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, ny).toInstant().toEpochMilli()

    @Test fun separatorPerDayAcrossDst() {
        // DST ended 2026-11-01 02:00 in New York.
        val times = listOf(at(2026, 10, 31, 23, 30), at(2026, 11, 1, 1, 30), at(2026, 11, 1, 23, 59), at(2026, 11, 2, 0, 1))
        val items = Timeline.build(times, { it }, ny)
        val seps = items.filterIsInstance<TimelineItem.DateSeparator>().map { it.date }
        assertEquals(listOf(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)), seps)
        assertEquals(7, items.size)
    }

    @Test fun zoneMatters() {
        val t = at(2026, 11, 19, 22) // 22:00 New York = next day in Kolkata
        val kolkata = Timeline.build(listOf(t), { it }, ZoneId.of("Asia/Kolkata"))
        assertEquals(TimelineItem.DateSeparator(LocalDate.of(2026, 11, 20)), kolkata.first())
    }

    @Test fun empty() {
        assertEquals(0, Timeline.build(emptyList<Long>(), { it }, ny).size)
    }
}
