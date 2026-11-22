package com.dns.sms.classify

/**
 * Finds one-time codes in SMS text. Pure and bounded: input is capped and the patterns are
 * simple alternations (no nested quantifiers), so hostile text cannot cause slow matching.
 */
object OtpDetector {
    private const val MAX_INPUT = 1000

    private val keyword = Regex(
        """\b(otp|one[- ]?time (?:password|pin|passcode|code)|verification code|security code|auth(?:entication)? code|login code|passcode|access code|confirmation code)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** 4–8 digit runs; a leading "G-" style prefix is allowed. */
    private val code = Regex("""(?<![\d.,/:])(?:[A-Z]-)?(\d{4,8})(?![\d,/:]|\.\d)""")

    private val currencyBefore = Regex("""(?:rs\.?|inr|₹|\$|usd)\s*$""", RegexOption.IGNORE_CASE)

    fun isOtp(body: String): Boolean = extract(body) != null

    /** The code, or null when the message isn't an OTP. When several numbers appear, the one nearest the keyword wins. */
    fun extract(body: String): String? {
        val text = body.take(MAX_INPUT)
        val keywords = keyword.findAll(text).map { it.range }.toList()
        if (keywords.isEmpty()) return null
        return code.findAll(text)
            .filter { m -> !currencyBefore.containsMatchIn(text.substring(maxOf(0, m.range.first - 5), m.range.first)) }
            .minByOrNull { m -> keywords.minOf { k -> distance(k, m.range) } }
            ?.groupValues?.get(1)
    }

    private fun distance(a: IntRange, b: IntRange): Int = when {
        b.first > a.last -> b.first - a.last
        a.first > b.last -> a.first - b.last
        else -> 0
    }
}
