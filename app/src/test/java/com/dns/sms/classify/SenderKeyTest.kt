package com.dns.sms.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SenderKeyTest {
    @Test fun dltPrefixesAndSuffixesCollapse() {
        assertEquals("HDFCBK", SenderKey.of("VM-HDFCBK"))
        assertEquals("HDFCBK", SenderKey.of("ad-hdfcbk-S"))
        assertEquals("HDFCBK", SenderKey.of(" JD-HDFCBK-T "))
    }

    @Test fun phoneNumbersCollapseToLast10Digits() {
        assertEquals("9800012345", SenderKey.of("+91 98000 12345"))
        assertEquals("9800012345", SenderKey.of("09800012345"))
        assertEquals("56161", SenderKey.of("56161")) // short code kept whole
    }

    @Test fun otherSendersUppercased() {
        assertEquals("AMAZON", SenderKey.of("Amazon"))
        assertEquals("A@B.COM", SenderKey.of("a@b.com"))
        assertNotEquals(SenderKey.of("VM-HDFCBK"), SenderKey.of("VM-ICICIB"))
    }

    @Test fun groupKeyIsPerThread() {
        assertEquals("thread:42", SenderKey.ofGroup(42))
    }
}
