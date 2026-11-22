package com.dns.sms.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsKeysTest {
    @Test fun sameInputsSameKey() {
        assertEquals(SmsKey.of(1, "VM-HDFCBK", 1000L, "hello"), SmsKey.of(1, "VM-HDFCBK", 1000L, "hello"))
    }

    @Test fun anyFieldChangesKey() {
        val k = SmsKey.of(1, "VM-HDFCBK", 1000L, "hello")
        assertNotEquals(k, SmsKey.of(1, "VM-HDFCBK", 1000L, "hellp"))
        assertNotEquals(k, SmsKey.of(2, "VM-HDFCBK", 1000L, "hello"))
        assertNotEquals(k, SmsKey.of(1, "VM-HDFCBK", 1001L, "hello"))
        assertNotEquals(k, SmsKey.of(1, "VM-HDFCBL", 1000L, "hello"))
        // Same characters split differently between address and body must not collide.
        assertNotEquals(SmsKey.of(1, "ab", 5L, "c"), SmsKey.of(1, "a", 5L, "bc"))
    }

    @Test fun unicodeBodies() {
        assertNotEquals(SmsKey.of(1, "a", 1L, "नमस्ते"), SmsKey.of(1, "a", 1L, "नमस्त"))
    }

    @Test fun membershipAndInRunDuplicates() {
        val b = SmsKeySet.Builder(initial = 2)
        val keys = (1..1000).map { SmsKey.of(1, "x", it.toLong(), "body $it") }
        keys.forEach(b::add)
        val set = b.build()
        keys.forEach { assertTrue(it in set) }
        assertFalse(SmsKey.of(1, "x", 5L, "other") in set)
        assertFalse(set.add(keys[10]))
        val fresh = SmsKey.of(2, "y", 9L, "new")
        assertTrue(set.add(fresh))
        assertFalse(set.add(fresh))
        assertTrue(fresh in set)
    }
}
