package com.dns.sms.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkFinderTest {
    private fun links(s: String) = LinkFinder.find(s).map { s.substring(it) }

    @Test fun finds() {
        assertEquals(listOf("i.airtel.in/Contact"), links("If this was not you click i.airtel.in/Contact N9BWuqauU1y"))
        assertEquals(listOf("https://amzn.example/t/abc123"), links("Track: https://amzn.example/t/abc123."))
        assertEquals(listOf("www.example.com"), links("Visit www.example.com, today"))
    }

    @Test fun ignoresNonLinks() {
        assertEquals(emptyList<String>(), links("Avbl.Bal Rs.500. Valid for 100 secs. a.m."))
    }

    @Test fun url() {
        assertEquals("https://i.airtel.in/Contact", LinkFinder.toUrl("i.airtel.in/Contact"))
        assertEquals("http://x.com/a", LinkFinder.toUrl("http://x.com/a"))
    }

    @Test fun lookalikeHostsShownAsPunycode() {
        // "аmazon" with a Cyrillic а (U+0430)
        val fake = "https://\u0430mazon.in/track"
        assertEquals(true, LinkFinder.isLookalike(fake))
        assertEquals("xn--mazon-3ve.in", LinkFinder.asciiHost(fake))
        assertEquals(false, LinkFinder.isLookalike("https://amazon.in/track"))
        assertEquals("amazon.in", LinkFinder.asciiHost("https://user@amazon.in:443/x?y"))
    }
}
