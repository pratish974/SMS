package com.dns.sms.security

import com.dns.sms.security.UrlRisk.Level
import com.dns.sms.security.UrlRisk.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRiskTest {
    private fun level(sender: String, body: String, url: String) = UrlRisk.assess(sender, body, url).level
    private fun reasons(sender: String, body: String, url: String) = UrlRisk.assess(sender, body, url).reasons

    @Test fun genuineBankIsSafe() {
        assertEquals(Level.SAFE, level("VM-HDFCBK-S", "Your statement is ready", "https://hdfcbank.com/x"))
        assertEquals(Level.SAFE, level("VM-HDFCBK-S", "Netbanking", "https://netbanking.hdfcbank.com/login"))
    }

    @Test fun genuineBrandsAreSafe() {
        assertEquals(Level.SAFE, level("AX-AMAZON", "Your order shipped", "https://www.amazon.in/gp/css/order"))
        assertEquals(Level.SAFE, level("AD-UIDAIN", "Your Aadhaar", "https://uidai.gov.in/en/"))
        assertEquals(Level.SAFE, level("AD-ITDEPT", "Refund issued", "https://incometax.gov.in/iec/foportal"))
    }

    @Test fun kycPhishingOnRiskyTldIsDangerous() {
        val r = UrlRisk.assess("VM-HDFCBK", "Your KYC is pending. Update now", "http://hdfc-kyc-update.xyz/login")
        assertEquals(Level.DANGEROUS, r.level)
        assertTrue(Reason.BRAND_IMPERSONATION in r.reasons)
        assertTrue(Reason.RISKY_TLD in r.reasons)
        assertTrue(Reason.NOT_HTTPS in r.reasons)
        assertTrue(Reason.SENDER_DOMAIN_MISMATCH in r.reasons)
    }

    @Test fun brandInSubdomainOfOtherSiteIsDangerous() {
        assertEquals(Level.DANGEROUS, level("9876501234", "Pay", "https://hdfcbank.com.secure-login.in/pay"))
        assertEquals(Level.DANGEROUS, level("AX-SBIINB", "Blocked", "https://sbi-yono-update.co/login"))
    }

    @Test fun shortenerIsSuspicious() {
        assertEquals(Level.SUSPICIOUS, level("JK-OFFERS", "Great deal", "https://bit.ly/abc"))
        assertTrue(Reason.SHORTENER in reasons("JK-OFFERS", "Great deal", "https://bit.ly/abc"))
    }

    @Test fun shortenerWithUrgentWordingEscalates() {
        assertEquals(Level.DANGEROUS, level("JK-OFFERS", "Urgent: click here to claim", "https://bit.ly/abc"))
    }

    @Test fun userinfoTrickIsDangerous() {
        val r = UrlRisk.assess("VM-SBIINB", "Login", "https://sbi.co.in@evil.com/login")
        assertEquals(Level.DANGEROUS, r.level)
        assertTrue(Reason.USERINFO_TRICK in r.reasons)
    }

    @Test fun ipAddressAndPortAreDangerous() {
        assertEquals(Level.DANGEROUS, level("9999999999", "see", "http://192.168.10.4/login"))
        assertTrue(Reason.IP_OR_PORT in reasons("X", "see", "http://203.0.113.9/a"))
        assertTrue(Reason.IP_OR_PORT in reasons("X", "see", "https://example.com:8443/a"))
        assertFalse(Reason.IP_OR_PORT in reasons("X", "see", "https://example.com:443/a"))
    }

    @Test fun punycodeAndNonAsciiAreDangerous() {
        assertEquals(Level.DANGEROUS, level("X", "pay", "https://xn--hdfcbnk-9ya.com/login"))
        assertEquals(Level.DANGEROUS, level("X", "pay", "https://hдfcbank.com/login"))
        assertTrue(Reason.LOOKALIKE in reasons("X", "pay", "https://hдfcbank.com/login"))
    }

    @Test fun rtlOverrideIsDangerous() {
        val url = "https://hdfcbank.com/\u202Egpj.exe"
        assertTrue(Reason.HIDDEN_CHARS in reasons("VM-HDFCBK", "x", url))
        assertEquals(Level.DANGEROUS, level("VM-HDFCBK", "x", url))
    }

    @Test fun hiddenCharHelpers() {
        val s = "ab\u200Bc\u202Ed\uFEFF"
        assertTrue(UrlRisk.hasHidden(s))
        assertEquals("abcd", UrlRisk.stripHidden(s))
        assertFalse(UrlRisk.hasHidden("plain text"))
    }

    @Test fun phoneNumberSenderWithBankWordingIsSuspicious() {
        val r = UrlRisk.assess("+919876543210", "Your KYC is pending, update your bank account", "https://my-update-page.com/a")
        assertEquals(Level.DANGEROUS, r.level) // unverified sender + scam wording escalates
        assertTrue(Reason.UNVERIFIED_SENDER in r.reasons)
        assertEquals(Level.SUSPICIOUS, level("9876543210", "Your debit card details", "https://example.com/a"))
    }

    @Test fun unknownPlainSiteIsUnknown() {
        assertEquals(Level.UNKNOWN, level("AB-NEWS", "Read this article", "https://example.com/story"))
        assertEquals(Level.UNKNOWN, level("AB-NEWS", "Read this article", "http://example.com/story"))
    }

    @Test fun scamWordsRaiseUnknownToSuspicious() {
        assertEquals(Level.SUSPICIOUS, level("AB-NEWS", "You have won a lottery! Claim now", "https://example.com/story"))
    }

    @Test fun scamWordsDoNotMarkGenuineLinks() {
        assertEquals(Level.SAFE, level("VM-HDFCBK-S", "Complete your KYC", "https://hdfcbank.com/kyc"))
    }

    @Test fun senderMismatchOnOfficialLookingSender() {
        val r = reasons("VM-HDFCBK-S", "Offer for you", "https://example.com/offer")
        assertTrue(Reason.SENDER_DOMAIN_MISMATCH in r)
        assertEquals(Level.SUSPICIOUS, level("VM-HDFCBK-S", "Offer for you", "https://example.com/offer"))
    }

    @Test fun unusualShape() {
        assertTrue(Reason.UNUSUAL_SHAPE in reasons("X", "see", "https://a.b.c.d.e.f.example.com/x"))
        assertTrue(Reason.UNUSUAL_SHAPE in reasons("X", "see", "https://example.com/" + "a".repeat(250)))
    }

    @Test fun electricityScam() {
        val r = UrlRisk.assess("9812345678", "Your electricity will be disconnected tonight. Pay now", "http://bescom-bill-pay.top/p")
        assertEquals(Level.DANGEROUS, r.level)
        assertTrue(Reason.BRAND_IMPERSONATION in r.reasons)
    }

    @Test fun shortBrandWordsDoNotMatchInsideOtherWords() {
        assertEquals(Level.UNKNOWN, level("X", "see", "https://praxis-news.com/a"))
        assertEquals(Level.UNKNOWN, level("X", "see", "https://publicrecords.com/a"))
    }

    @Test fun hugeInputStaysFast() {
        val t0 = System.nanoTime()
        UrlRisk.assess("X", "kyc ".repeat(50_000), "https://" + "a.".repeat(50_000) + "com")
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 1_000)
    }

    @Test fun otpScamPattern() {
        assertTrue(ScamSignals.otpScam("Share the OTP 482913 with our agent to get refund"))
        assertTrue(ScamSignals.otpScam("Your OTP is 482913. Tell it to the executive on call"))
        assertFalse(ScamSignals.otpScam("482913 is your OTP for login. Do not share it with anyone. If not you, call 1800 1234"))
        assertFalse(ScamSignals.otpScam("Your OTP is 482913. Never share this with anyone, bank executives never ask for it."))
        assertFalse(ScamSignals.otpScam("Share the news with our agent"))
    }
}
