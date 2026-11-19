package com.dns.sms.parse

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun indianGrouping() {
        assertEquals("₹ 10,72,270.50", Money.formatInr(BigDecimal("1072270.50")))
        assertEquals("₹ 1,21,416", Money.formatInr(BigDecimal("121416.00")))
        assertEquals("₹ 22,397.55", Money.formatInr(BigDecimal("22397.55")))
        assertEquals("₹ 0", Money.formatInr(BigDecimal.ZERO))
        assertEquals("₹ 999", Money.formatInr(BigDecimal("999")))
        assertEquals("₹ 1,00,00,000", Money.formatInr(BigDecimal("10000000")))
        assertEquals("₹ 3,423.05", Money.formatInr(BigDecimal("3423.05")))
        assertEquals("-₹ 500", Money.formatInr(BigDecimal("-500")))
    }

    @Test fun parse() {
        assertEquals(BigDecimal("259288"), Money.parse("2,59,288/-"))
        assertEquals(BigDecimal("1072270.50"), Money.parse("10,72,270.50"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
    }
}
