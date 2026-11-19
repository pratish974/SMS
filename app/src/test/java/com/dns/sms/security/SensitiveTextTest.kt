package com.dns.sms.security

import com.dns.sms.security.SensitiveText.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class SensitiveTextTest {
    private fun d(t: String) = SensitiveText.detect(t)

    @Test fun otp() {
        assertEquals(setOf(Kind.OTP), d("my otp is 482913"))
        assertEquals(setOf(Kind.OTP), d("the code is 4829"))
    }

    @Test fun ordinaryTextIsClean() {
        assertEquals(emptySet<Kind>(), d("See you at 7:30, room 4021 is free"))
        assertEquals(emptySet<Kind>(), d("Call me on 9876543210"))
        assertEquals(emptySet<Kind>(), d(""))
    }

    @Test fun cardNumberPassesLuhnOnly() {
        assertEquals(setOf(Kind.CARD_NUMBER), d("card 4111 1111 1111 1111"))
        assertEquals(setOf(Kind.CARD_NUMBER), d("4111-1111-1111-1111"))
        assertEquals(setOf(Kind.CARD_NUMBER), d("5500005555555559"))
        assertEquals(emptySet<Kind>(), d("order 4111 1111 1111 1112"))
    }

    @Test fun cvv() {
        assertEquals(setOf(Kind.CVV), d("cvv 123"))
        assertEquals(setOf(Kind.CVV), d("CVV: 4567"))
        assertEquals(setOf(Kind.CVV), d("123 cvv"))
        assertEquals(emptySet<Kind>(), d("cvv is on the back"))
    }

    @Test fun aadhaar() {
        assertEquals(setOf(Kind.AADHAAR), d("aadhaar 2345 6789 0123"))
        assertEquals(setOf(Kind.AADHAAR), d("234567890123"))
        assertEquals(emptySet<Kind>(), d("number 123456789012"))
        assertEquals(emptySet<Kind>(), d("number 034567890123"))
    }

    @Test fun pan() {
        assertEquals(setOf(Kind.PAN), d("my pan ABCDE1234F"))
        assertEquals(emptySet<Kind>(), d("ABCDE12345"))
    }

    @Test fun upiPin() {
        assertEquals(setOf(Kind.UPI_PIN), d("my UPI PIN is private"))
        assertEquals(setOf(Kind.UPI_PIN), d("mpin 1234"))
    }

    @Test fun several() {
        assertEquals(setOf(Kind.CARD_NUMBER, Kind.CVV), d("4111111111111111 cvv 321"))
    }
}
