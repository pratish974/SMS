package com.dns.sms.parse

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    private val number = Regex("""^\d[\d,]*(?:\.\d{1,2})?$""")

    /** "10,72,270.50" -> 1072270.50. Null if not a plain positive amount. */
    fun parse(raw: String): BigDecimal? {
        val t = raw.trim().trimEnd('.', '/', '-')
        if (!number.matches(t)) return null
        return runCatching { BigDecimal(t.replace(",", "")) }.getOrNull()
    }

    /**
     * Indian grouping (lakh/crore): 1072270.5 -> "₹ 10,72,270.50"; whole amounts drop ".00".
     */
    fun formatInr(amount: BigDecimal): String {
        val neg = amount.signum() < 0
        val scaled = amount.abs().setScale(2, RoundingMode.HALF_UP)
        val whole = scaled.toBigInteger().toString()
        val paise = scaled.remainder(BigDecimal.ONE).movePointRight(2).toInt()
        val grouped = if (whole.length <= 3) whole else {
            val last3 = whole.takeLast(3)
            val rest = whole.dropLast(3)
            rest.reversed().chunked(2).joinToString(",").reversed() + "," + last3
        }
        val frac = if (paise == 0) "" else "." + paise.toString().padStart(2, '0')
        return (if (neg) "-₹ " else "₹ ") + grouped + frac
    }
}
