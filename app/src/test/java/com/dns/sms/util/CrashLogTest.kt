package com.dns.sms.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {
    @Test fun dropsExceptionMessagesButKeepsTypesAndFrames() {
        val secret = "OTP 482913 from +919876543210"
        val e = IllegalStateException("outer $secret", NumberFormatException("For input string: \"$secret\""))
        val out = CrashLog.sanitize(e)
        assertFalse(out.contains("482913"))
        assertFalse(out.contains("9876543210"))
        assertTrue(out.contains("java.lang.IllegalStateException"))
        assertTrue(out.contains("Caused by: java.lang.NumberFormatException"))
        assertTrue(out.contains("at com.dns.sms.util.CrashLogTest"))
    }
}
