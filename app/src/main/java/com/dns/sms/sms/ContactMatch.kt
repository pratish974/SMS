package com.dns.sms.sms

/** Pure matching for the New message contact search (unit tested). */
object ContactMatch {
    /** Lower is better; null = no match. */
    fun rank(name: String?, number: String, query: String): Int? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val n = name.orEmpty()
        val digits = q.filter { it.isDigit() }
        return when {
            n.startsWith(q, ignoreCase = true) -> 0
            n.split(' ', '-', '.').any { it.startsWith(q, ignoreCase = true) } -> 1
            n.contains(q, ignoreCase = true) -> 2
            digits.length >= 2 && digits.length == q.count { !it.isWhitespace() && it != '+' && it != '-' } &&
                number.filter { it.isDigit() }.contains(digits) -> 3
            else -> null
        }
    }

    /** Character range in [text] to highlight for [query] (case-insensitive), or null. */
    fun highlight(text: String, query: String): IntRange? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val i = text.indexOf(q, ignoreCase = true)
        return if (i < 0) null else i until i + q.length
    }

    /** Typed text that should be offered as "Send to …". */
    fun asNumber(query: String): String? {
        val t = query.trim()
        return if (PhoneNumbers.isPhoneLike(t) && t.count { it.isDigit() } >= 3) PhoneNumbers.normalize(t) else null
    }
}
