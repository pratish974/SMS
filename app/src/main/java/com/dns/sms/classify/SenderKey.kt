package com.dns.sms.classify

import com.dns.sms.sms.PhoneNumbers

/**
 * Stable identity for "remember this sender". Indian DLT sender ids arrive with varying operator
 * prefixes and type suffixes ("VM-HDFCBK", "AD-HDFCBK-S"), so they collapse to the brand ("HDFCBK").
 * Phone numbers collapse to their last 10 digits ("+91 98000 12345" == "09800012345").
 */
object SenderKey {
    private val dlt = Regex("""^[A-Z]{2}-([A-Z0-9]{3,12})(?:-[PSTG])?$""")

    fun of(address: String): String {
        val t = address.trim()
        if (PhoneNumbers.isPhoneLike(t)) {
            val digits = t.filter { it.isDigit() }
            return if (digits.length >= 10) digits.takeLast(10) else digits
        }
        val upper = t.uppercase()
        return dlt.matchEntire(upper)?.groupValues?.get(1) ?: upper
    }

    /** Group conversations have no single sender; their override is per thread. */
    fun ofGroup(threadId: Long): String = "thread:$threadId"
}
