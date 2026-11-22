package com.dns.sms.security

import com.dns.sms.classify.OtpDetector

/**
 * Spots secrets a person is about to type into an SMS, so we can ask first. Scammers talk people into
 * texting OTPs, card numbers, CVVs, PINs and Aadhaar numbers. Pure Kotlin; input capped, simple patterns.
 */
object SensitiveText {
    enum class Kind { OTP, CARD_NUMBER, CVV, AADHAAR, PAN, UPI_PIN }

    private const val MAX_INPUT = 2000

    private val digitRun = Regex("""(?<!\d)\d(?:[ -]?\d){12,18}(?!\d)""")
    private val aadhaar = Regex("""(?<![\d-])(?<!\d[ -])[2-9]\d{3}[ -]?\d{4}[ -]?\d{4}(?![\d-])(?![ -]\d)""")
    private val cvv = Regex("""\b(?:cvv2?|cvc)\b\D{0,12}\d{3,4}(?!\d)|(?<!\d)\d{3,4}\s{0,3}\b(?:cvv2?|cvc)\b""", RegexOption.IGNORE_CASE)
    private val pan = Regex("""(?<![A-Z0-9])[A-Z]{5}[0-9]{4}[A-Z](?![A-Z0-9])""")
    private val upiPin = Regex("""\b(?:upi\s*pin|mpin|m-pin)\b""", RegexOption.IGNORE_CASE)
    private val bareCode = Regex("""(?<!\d)\d{4,8}(?!\d)""")
    private val codeWords = Regex("""\b(?:otp|passcode|verification code|security code|code is|my code)\b""", RegexOption.IGNORE_CASE)

    fun detect(text: String): Set<Kind> {
        val t = text.take(MAX_INPUT)
        val found = linkedSetOf<Kind>()
        if (OtpDetector.extract(t) != null || (codeWords.containsMatchIn(t) && bareCode.containsMatchIn(t))) found += Kind.OTP
        if (digitRun.findAll(t).any { luhn(it.value.filter(Char::isDigit)) }) found += Kind.CARD_NUMBER
        if (cvv.containsMatchIn(t)) found += Kind.CVV
        if (aadhaar.containsMatchIn(t)) found += Kind.AADHAAR
        if (pan.containsMatchIn(t)) found += Kind.PAN
        if (upiPin.containsMatchIn(t)) found += Kind.UPI_PIN
        return found
    }

    private fun luhn(d: String): Boolean {
        if (d.length !in 13..19) return false
        var sum = 0
        d.reversed().forEachIndexed { i, c ->
            var n = c - '0'
            if (i % 2 == 1) { n *= 2; if (n > 9) n -= 9 }
            sum += n
        }
        return sum % 10 == 0
    }
}
