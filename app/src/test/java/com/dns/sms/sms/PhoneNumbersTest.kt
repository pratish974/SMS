package com.dns.sms.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {
    @Test fun normalize() {
        assertEquals("+919800012345", PhoneNumbers.normalize(" +91 98000-12345 "))
        assertEquals("09800012345", PhoneNumbers.normalize("(0) 98000 12345"))
        assertEquals("VM-HDFCBK", PhoneNumbers.normalize(" VM-HDFCBK "))
    }

    @Test fun sameNumberAcrossPrefixes() {
        assertTrue(PhoneNumbers.same("+919800012345", "09800012345"))
        assertTrue(PhoneNumbers.same("+91 98000 12345", "9800012345"))
        assertTrue(PhoneNumbers.same("vm-hdfcbk", "VM-HDFCBK"))
        assertFalse(PhoneNumbers.same("+919800012345", "+919800012346"))
        assertFalse(PhoneNumbers.same("12345", "912345")) // short codes need exact match
    }

    @Test fun initials() {
        assertEquals("AB", PhoneNumbers.initials("asha  bhat"))
        assertEquals("R", PhoneNumbers.initials("Ravi"))
        assertEquals("#", PhoneNumbers.initials("+91 98000 12345"))
        assertEquals("अ", PhoneNumbers.initials("अमित"))
    }

    @Test fun colorIndexIsStableAndInRange() {
        val a = PhoneNumbers.colorIndex("+919800012345", 8)
        assertEquals(a, PhoneNumbers.colorIndex("+91 98000 12345", 8))
        assertTrue(a in 0 until 8)
    }
}
