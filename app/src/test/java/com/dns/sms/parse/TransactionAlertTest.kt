package com.dns.sms.parse

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Anonymised sample messages (made-up numbers in real-world shapes). */
class TransactionAlertTest {
    @Test fun upiDebitIsRed() {
        val t = TransactionAlert.from("AD-ICICIT-S", "ICICI Bank Acct XX439 debited for Rs 3500.00 on 24-Nov-26; A PERSON credited. UPI:111111111111. Call 18000000000 for dispute. SMS BLOCK 439 to 9000000000.")!!
        assertTrue(t.debit)
        assertEquals(BigDecimal("3500.00"), t.amount)
        assertEquals("UPI", t.mode)
        assertEquals("ICICI", t.bank)
        assertEquals("XXX439", t.mask)
    }

    @Test fun impsDebitOnSenderSide() {
        val t = TransactionAlert.from("BP-ICICIT-S", "ICICI Bank Acct XX439 debited with Rs 200.00 on 24-Nov-26 & Acct XX487 credited.IMPS:632800000000. Call 18000000000 for dispute or SMS BLOCK 439 to 9000000000")!!
        assertTrue(t.debit)
        assertEquals(BigDecimal("200.00"), t.amount)
        assertEquals("IMPS", t.mode)
    }

    @Test fun impsCreditIsGreen() {
        val t = TransactionAlert.from("AX-SBIPSG-T", "Dear Customer, Your a/c no. XXXXXXXX3487 is credited by Rs.200.00 on 24-11-26 by a/c linked to mobile 9XXXXXX000-A PERSON (IMPS Ref# 632800000000)-SBI")!!
        assertFalse(t.debit)
        assertEquals("SBI", t.bank)
        assertEquals("XX3487", t.mask)
        assertEquals("IMPS", t.mode)
    }

    @Test fun cardSpendUsesCardNumber() {
        val t = TransactionAlert.from("BP-ICICIT-S", "INR 1,995.00 spent using ICICI Bank Card XX2006 on 20-Nov-26 on RETAIL. Avl Limit: INR 2,78,787.00. If not you, call 18000000000")!!
        assertTrue(t.debit)
        assertEquals(BigDecimal("1995.00"), t.amount)
        assertEquals("Card", t.mode)
        assertEquals("XX2006", t.mask)
    }

    @Test fun notMoneyMovement() {
        assertNull(TransactionAlert.from("AD-ICICIT-S", "Your AutoPay mandate with MERCHANT is successfully created from 19-Nov-26 to 31-Dec-36 for Rs 1999.00, RRN 632300000000-ICICI Bank."))
        assertNull(TransactionAlert.from("AD-ICICIT-S", "Rs 500 will be debited from your account on 25-Nov for your SIP."))
        assertNull(TransactionAlert.from("AD-AIRINF-T", "<#> 3511 is your OTP to login. Valid for 100 secs."))
        assertNull(TransactionAlert.from("+919800000000", "I sent you Rs 500, debited from my account"))
    }
}
