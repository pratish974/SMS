package com.dns.sms.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {
    @Test fun escapesWildcards() {
        assertEquals("%50\\% off%", SearchQuery.likePattern("50% off"))
        assertEquals("%a\\_b\\\\c%", SearchQuery.likePattern("a_b\\c"))
        assertEquals("%otp%", SearchQuery.likePattern("otp"))
    }

    @Test fun normalizes() {
        assertNull(SearchQuery.normalize(" a "))
        assertEquals("ab", SearchQuery.normalize(" ab "))
        assertEquals(SearchQuery.MAX_LENGTH, SearchQuery.normalize("x".repeat(500))!!.length)
    }

    @Test fun ftsMatchQuotesPrefixWords() {
        assertEquals("\"Rs*\" \"432*\"", SearchQuery.ftsMatch("Rs 432"))
        assertEquals("\"debited*\"", SearchQuery.ftsMatch("  debited "))
        assertEquals("\"a b*\"", SearchQuery.ftsMatch("a-b\""))
        assertEquals("\"\u0928\u092E\u0938\u094D\u0924\u0947*\"", SearchQuery.ftsMatch("\u0928\u092E\u0938\u094D\u0924\u0947"))
        assertNull(SearchQuery.ftsMatch("-- %%"))
    }
}
