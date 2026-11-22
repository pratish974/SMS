package com.dns.sms.sms

/** Pure helpers for addresses. Alphanumeric sender ids ("VM-HDFCBK") are kept as-is. */
object PhoneNumbers {

    fun isPhoneLike(address: String): Boolean {
        val t = address.trim()
        return t.isNotEmpty() && t.all { it.isDigit() || it in "+-() ." } && t.any { it.isDigit() }
    }

    /** Strips formatting from phone numbers; leaves other addresses trimmed. */
    fun normalize(address: String): String {
        val t = address.trim()
        if (!isPhoneLike(t)) return t
        val digits = t.filter { it.isDigit() }
        return if (t.startsWith("+")) "+$digits" else digits
    }

    /** Same person? Phone numbers compare on their last 10 digits (handles +91 / 0 prefixes). */
    fun same(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.equals(nb, ignoreCase = true)) return true
        if (!isPhoneLike(na) || !isPhoneLike(nb)) return false
        val da = na.filter { it.isDigit() }
        val db = nb.filter { it.isDigit() }
        if (da.length < 7 || db.length < 7) return false
        val n = minOf(10, da.length, db.length)
        return da.takeLast(n) == db.takeLast(n)
    }

    /** Up to two initials for a display name; "#" for bare numbers. */
    fun initials(displayName: String): String {
        if (isPhoneLike(displayName)) return "#"
        val words = displayName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val letters = words.mapNotNull { w -> w.firstOrNull { it.isLetterOrDigit() } }.take(2)
        return letters.joinToString("").uppercase().ifEmpty { "#" }
    }

    /** Stable index into an avatar palette of [size] colors. */
    fun colorIndex(key: String, size: Int): Int = Math.floorMod(normalize(key).hashCode(), size)
}
