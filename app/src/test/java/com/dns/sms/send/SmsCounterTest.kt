package com.dns.sms.send

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsCounterTest {
    @Test fun empty() {
        val expected = SmsCounter.Count(parts = 0, remaining = 160, unicode = false)
        val actual = SmsCounter.count("")
        assertEquals(expected, actual)
    }

    @Test fun gsmSingleAndSplit() {
        fun check(expected: SmsCounter.Count, text: String) {
            val actual = SmsCounter.count(text)
            assertEquals(expected, actual)
        }
        check(SmsCounter.Count(1, 0, false), "a".repeat(160))
        check(SmsCounter.Count(2, 145, false), "a".repeat(161))
        check(SmsCounter.Count(2, 0, false), "a".repeat(306))
        assertEquals(3, SmsCounter.count("a".repeat(307)).parts)
    }

    @Test fun extendedCharsCountDouble() {
        val euro = SmsCounter.count("€")
        assertEquals(SmsCounter.Count(1, 158, false), euro)
        assertEquals(2, SmsCounter.count("{".repeat(81)).parts)
    }

    @Test fun hindiAndEmojiAreUnicode() {
        val c = SmsCounter.count("नमस्ते")
        assertTrue(c.unicode)
        assertEquals(70 - "नमस्ते".length, c.remaining)
        assertEquals(2, SmsCounter.count("a".repeat(70) + "👋").parts) // 72 units > 70
        assertEquals(1, SmsCounter.count("👋").parts)
        assertEquals(68, SmsCounter.count("👋").remaining) // emoji = 2 units
    }

    @Test fun showOnlyNearLimitOrSplit() {
        assertFalse(SmsCounter.worthShowing(SmsCounter.count("hello")))
        assertTrue(SmsCounter.worthShowing(SmsCounter.count("a".repeat(145))))
        assertTrue(SmsCounter.worthShowing(SmsCounter.count("a".repeat(200))))
    }
}
