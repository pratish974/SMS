package com.dns.sms.parse

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateExtractorTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun ref(y: Int, m: Int, d: Int) = ZonedDateTime.of(y, m, d, 10, 0, 0, 0, ist)
    private fun first(s: String, r: ZonedDateTime = ref(2026, 11, 19)) = DateExtractor.findAll(s, r).first()

    @Test fun numericIsDayFirst() {
        assertEquals(LocalDate.of(2026, 3, 5), first("Due on 05/03/2026").date)
        assertEquals(LocalDate.of(2026, 12, 25), first("Due 25-12-26").date)
        assertEquals(LocalDate.of(2026, 11, 25), first("2026-11-25 deadline").date)
    }

    @Test fun monthNames() {
        assertEquals(LocalDate.of(2026, 11, 25), first("pay by 25 Nov 2026").date)
        assertEquals(LocalDate.of(2026, 11, 25), first("pay by 25-Nov-26").date)
        assertEquals(LocalDate.of(2026, 12, 5), first("on 5th December").date)
        assertEquals(LocalDate.of(2026, 12, 7), first("Dec 7, 2026 at 6:30 PM").date)
    }

    @Test fun yearRollsForward() {
        assertEquals(LocalDate.of(2027, 1, 5), first("due 05 Jan", ref(2026, 12, 28)).date)
        assertEquals(LocalDate.of(2026, 11, 10), first("on 10 Nov", ref(2026, 11, 19)).date) // recent past stays
    }

    @Test fun times() {
        assertEquals(LocalTime.of(18, 30), first("Show on 20 Nov 2026 6:30 PM").time)
        assertEquals(LocalTime.of(7, 5), first("Dep 21/11/2026 07:05 hrs").time)
        assertEquals(LocalTime.of(21, 0), first("on 21 Nov at 9 pm").time)
    }

    @Test fun invalidDatesSkipped() {
        assertTrue(DateExtractor.findAll("31/02/2026 and 45/13/2026", ref(2026, 11, 19)).isEmpty())
    }

    @Test fun bounded() {
        val s = "1/".repeat(20_000) + " Jan ".repeat(20_000)
        val start = System.nanoTime()
        DateExtractor.findAll(s, ref(2026, 1, 1))
        assertTrue((System.nanoTime() - start) / 1_000_000 < 500)
    }
}
