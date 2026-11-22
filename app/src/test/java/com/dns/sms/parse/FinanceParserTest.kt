package com.dns.sms.parse

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Anonymised sample messages (made-up numbers in real-world shapes). */
class FinanceParserTest {
    private fun p(sender: String, body: String) = FinanceParser.parse(sender, body)

    @Test fun debitWithAvailableBalance() {
        val u = p("AD-ICICIT-S", "ICICI Bank Acct XX439 debited for Rs 2463.60 on 19-Nov-26; MERCHANT credited. UPI:123456789012. Avbl Bal Rs 10,72,270.50")!!
        assertEquals("ICICI", u.bank)
        assertEquals("XXX439", u.mask)
        assertEquals(BigDecimal("1072270.50"), u.balance)
        assertEquals("DEBIT", u.txnType)
        assertEquals(BigDecimal("2463.60"), u.txnAmount)
    }

    @Test fun inrBeforeVerb() {
        val u = p("VM-NKGSBK-S", "NKGSB a/c X495 is DEBITED INR 1534.00 on 17-11-26. Avl Bal: INR 5,24,685.73")!!
        assertEquals("NKGSB", u.bank)
        assertEquals("XXX495", u.mask)
        assertEquals(BigDecimal("524685.73"), u.balance)
        assertEquals("DEBIT", u.txnType)
        assertEquals(BigDecimal("1534.00"), u.txnAmount)
    }

    @Test fun creditFourDigitAccount() {
        val u = p("JD-HDFCBK", "Rs.25,000.00 credited to HDFC Bank A/c XX1234 on 01-11-26 by NEFT. Avl bal: Rs.73,322")!!
        assertEquals("HDFC", u.bank)
        assertEquals("XX1234", u.mask)
        assertEquals("CREDIT", u.txnType)
        assertEquals(BigDecimal("25000.00"), u.txnAmount)
        assertEquals(BigDecimal("73322"), u.balance)
    }

    @Test fun epfoPassbook() {
        val u = p("VM-EPFOHO", "Dear Member, your passbook balance against XXXXXXXX7580 is Rs. 2,59,288/-. Contribution of Rs 3,600 for Oct-26 received.")!!
        assertEquals("EPFO", u.bank)
        assertEquals("XX7580", u.mask)
        assertEquals(BigDecimal("259288"), u.balance)
    }

    @Test fun ignoresOffersAndOtp() {
        assertNull(p("AD-SHOPCO-P", "Get cashback of Rs 500 on your account. Offer valid till Sunday"))
        assertNull(p("VM-ICICIT", "OTP 552019 for txn of Rs 4,500 on card XX9876. Do not share"))
        assertNull(p("+919800012345", "Lunch at 1?"))
    }

    @Test fun neverThrowsOnGarbage() {
        val junk = "Rs " + "9".repeat(5000) + " a/c " + ",".repeat(5000)
        val start = System.nanoTime()
        p("X", junk)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 300)
    }
}
