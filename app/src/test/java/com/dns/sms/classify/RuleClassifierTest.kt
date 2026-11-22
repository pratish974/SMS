package com.dns.sms.classify

import com.dns.sms.classify.Category.PERSONAL
import com.dns.sms.classify.Category.PROMOTIONS
import com.dns.sms.classify.Category.TRANSACTIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleClassifierTest {

    private fun classify(sender: String, body: String, contact: Boolean = false, override: Category? = null) =
        RuleClassifier.classify(sender, body, contact, override)

    @Test fun overrideWinsOverEverything() {
        assertEquals(PERSONAL, classify("AD-SHOPCO-P", "50% off sale", contact = false, override = PERSONAL))
    }

    @Test fun savedContactIsPersonal() {
        assertEquals(PERSONAL, classify("+919800000000", "Your OTP is 1234", contact = true))
    }

    @Test fun promotionalSuffix() {
        assertEquals(PROMOTIONS, classify("AD-SHOPCO-P", "hello"))
    }

    @Test fun transactionalServiceGovernmentSuffixes() {
        assertEquals(TRANSACTIONS, classify("VM-MYBANK-T", "hello"))
        assertEquals(TRANSACTIONS, classify("JD-MYBANK-S", "hello"))
        assertEquals(TRANSACTIONS, classify("CP-GOVTIN-G", "hello"))
        assertEquals(TRANSACTIONS, classify("vm-mybank-s", "hello"))
    }

    @Test fun suffixBeatsKeywords() {
        assertEquals(PROMOTIONS, classify("AD-SHOPCO-P", "Rs 500 credited as cashback"))
    }

    @Test fun otpAndTransactionKeywords() {
        assertEquals(TRANSACTIONS, classify("+919800000000", "123456 is your OTP. Do not share."))
        assertEquals(TRANSACTIONS, classify("+919800000000", "Rs.1,200.00 debited from A/c XX1234"))
        assertEquals(TRANSACTIONS, classify("MYBANK", "Avbl bal: Rs 10,000"))
        assertEquals(TRANSACTIONS, classify("98000", "UPI txn ref 1234 successful"))
    }

    @Test fun promotionalKeywords() {
        assertEquals(PROMOTIONS, classify("SHOPCO", "Flat 50% off this weekend"))
        assertEquals(PROMOTIONS, classify("SHOPCO", "Big SALE starts now. T&C apply"))
        assertEquals(PROMOTIONS, classify("SHOPCO", "Exclusive offers for you"))
    }

    @Test fun keywordNeedsWordBoundary() {
        // "salesman" must not match "sale".
        assertEquals(PERSONAL, classify("+919800000000", "The salesman will come at 5"))
    }

    @Test fun numericSenderDefaultsToPersonal() {
        assertEquals(PERSONAL, classify("+91 98000-00000", "See you tonight"))
        assertEquals(PERSONAL, classify("9800000000", "ok"))
    }

    @Test fun alphanumericSenderDefaultsToTransactions() {
        assertEquals(TRANSACTIONS, classify("MYBANK", "Hello there"))
        assertEquals(TRANSACTIONS, classify("VM-MYBANK", "Hello there"))
    }

    @Test fun blankInputs() {
        assertEquals(PERSONAL, classify("9800000000", ""))
        assertEquals(TRANSACTIONS, classify("", ""))
    }

    @Test fun keywordBeyondInputCapIsIgnored() {
        val body = "a".repeat(RuleClassifier.MAX_INPUT_LENGTH) + " otp"
        assertEquals(PERSONAL, classify("9800000000", body))
    }

    @Test fun hostileLongInputIsFast() {
        val body = "a/".repeat(50_000) + "%".repeat(50_000) + " ".repeat(50_000)
        val start = System.nanoTime()
        classify("9".repeat(100_000) + "x", body)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took ${ms}ms", ms < 200)
    }
}
