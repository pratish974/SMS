package com.dns.sms.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContactMatchTest {
    @Test fun rankOrder() {
        assertEquals(0, ContactMatch.rank("Prakash Gavali", "+919869772022", "pra"))
        assertEquals(1, ContactMatch.rank("Ravi Prakash", "+911", "pra"))
        assertEquals(2, ContactMatch.rank("Apratim", "+911", "pra"))
        assertEquals(3, ContactMatch.rank("Asha", "+91 98697 72022", "98697"))
        assertNull(ContactMatch.rank("Asha", "+919869772022", "zz"))
        assertNull(ContactMatch.rank("Asha", "+919869772022", "9")) // one digit is too broad
        assertNull(ContactMatch.rank("Asha", "+919869772022", "  "))
    }

    @Test fun mixedTextIsNotANumberSearch() {
        assertNull(ContactMatch.rank("Asha", "+919869772022", "98a"))
    }

    @Test fun highlight() {
        assertEquals(0..2, ContactMatch.highlight("Prakash", "PRA"))
        assertEquals(5..7, ContactMatch.highlight("Ravi Prakash", "pra"))
        assertNull(ContactMatch.highlight("Ravi", "x"))
    }

    @Test fun asNumber() {
        assertEquals("+919869772022", ContactMatch.asNumber("+91 98697 72022"))
        assertEquals("56161", ContactMatch.asNumber("56161"))
        assertNull(ContactMatch.asNumber("Prakash"))
        assertNull(ContactMatch.asNumber("12"))
    }
}
