package com.dns.sms.classify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtpDetectorTest {
    private fun otp(s: String) = OtpDetector.extract(s)

    @Test fun commonFormats() {
        assertEquals("123456", otp("123456 is your OTP for login. Do not share it with anyone."))
        assertEquals("4821", otp("Your OTP is 4821. Valid for 10 minutes."))
        assertEquals("552019", otp("Your verification code is: 552019"))
        assertEquals("482913", otp("G-482913 is your Google verification code."))
        assertEquals("908172", otp("Use 908172 as one time password for txn of Rs 2,463.00 on card XX4392"))
    }

    @Test fun ignoresAmountsCardsAndDates() {
        assertEquals("7788", otp("Rs 1234 will be debited. OTP 7788 for confirmation."))
        assertEquals("390127", otp("OTP for INR 4500.00 is 390127. Card ending 9876."))
        assertEquals("5512", otp("Your login code 5512 sent on 19/11/2026 10:30"))
    }

    @Test fun notAnOtp() {
        assertNull(otp("Rs.1,200.00 debited from A/c XX1234 on 19-Nov-26"))
        assertNull(otp("Your OTP will be sent shortly")) // keyword, no code
        assertNull(otp("Meet at 1830 near gate 4"))
        assertNull(otp(""))
    }

    @Test fun boundedOnHostileInput() {
        val s = "otp " + "1".repeat(100_000) + " 9".repeat(50_000)
        val start = System.nanoTime()
        otp(s)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 200)
    }
}
