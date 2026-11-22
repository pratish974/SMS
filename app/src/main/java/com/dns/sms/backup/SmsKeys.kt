package com.dns.sms.backup

import com.dns.sms.sms.PhoneNumbers

/**
 * Duplicate detection for restore. A 64-bit FNV-1a hash per SMS replaces the old "type|address|date|hash"
 * strings: a million messages cost 8 MB (one sorted LongArray) instead of 100+ MB of String objects.
 * A collision (about 1 in 10^13 per pair) would only skip one restored message.
 */
object SmsKey {
    private const val OFFSET = -3750763034362895579L // 0xcbf29ce484222325
    private const val PRIME = 1099511628211L

    /** Same function for stored and incoming messages, so equal messages get equal keys. */
    fun of(type: Int, address: String, date: Long, body: String): Long {
        var h = OFFSET
        h = mix(h, type.toLong())
        h = mix(h, date)
        for (c in PhoneNumbers.normalize(address)) h = mixChar(h, c.code)
        h = mixChar(h, SEP)
        for (c in body) h = mixChar(h, c.code)
        return h
    }

    private const val SEP = 0x1F

    private fun mixChar(h: Long, c: Int): Long = (h xor (c and 0xFF).toLong()).times(PRIME).let { (it xor ((c ushr 8) and 0xFF).toLong()).times(PRIME) }

    private fun mix(h: Long, v: Long): Long {
        var x = h
        for (i in 0 until 8) x = (x xor ((v ushr (i * 8)) and 0xFF)).times(PRIME)
        return x
    }
}

/** Keys already on the phone (sorted array, binary search) plus keys added during this restore. */
class SmsKeySet private constructor(private val existing: LongArray) {
    private val added = HashSet<Long>()

    /** True if [key] was unseen; it is remembered either way. */
    fun add(key: Long): Boolean = existing.binarySearch(key) < 0 && added.add(key)

    operator fun contains(key: Long): Boolean = existing.binarySearch(key) >= 0 || key in added

    class Builder(initial: Int = 1 shl 16) {
        private var keys = LongArray(initial)
        private var size = 0

        fun add(key: Long) {
            if (size == keys.size) keys = keys.copyOf(size * 2)
            keys[size++] = key
        }

        fun build(): SmsKeySet = SmsKeySet(keys.copyOf(size).also { it.sort() })
    }
}
