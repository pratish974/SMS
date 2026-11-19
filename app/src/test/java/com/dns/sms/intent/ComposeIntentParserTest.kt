package com.dns.sms.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposeIntentParserTest {
    @Test fun singleRecipientWithBody() {
        val d = ComposeIntentParser.parse("smsto:+919800012345?body=Hello%20there")!!
        assertEquals(listOf("+919800012345"), d.recipients)
        assertEquals("Hello there", d.body)
    }

    @Test fun plusIsNotDecodedToSpace() {
        assertEquals(listOf("+15551234"), ComposeIntentParser.parse("sms:+15551234")!!.recipients)
        assertEquals("1+1", ComposeIntentParser.parse("sms:5?body=1+1")!!.body)
    }

    @Test fun multipleRecipientsAndEncodedSeparators() {
        val d = ComposeIntentParser.parse("smsto:5551,5552%3B5553;5551")!!
        assertEquals(listOf("5551", "5552", "5553"), d.recipients)
    }

    @Test fun utf8Body() {
        assertEquals("नमस्ते", ComposeIntentParser.parse("sms:1?body=%E0%A4%A8%E0%A4%AE%E0%A4%B8%E0%A5%8D%E0%A4%A4%E0%A5%87")!!.body)
    }

    @Test fun rejectsOtherSchemes() {
        assertNull(ComposeIntentParser.parse("tel:5551"))
        assertNull(ComposeIntentParser.parse("javascript:alert(1)"))
        assertNull(ComposeIntentParser.parse(null))
    }

    @Test fun capsAndSanitizes() {
        val many = (1..50).joinToString(",") { "55500$it" }
        assertEquals(ComposeIntentParser.MAX_RECIPIENTS, ComposeIntentParser.parse("sms:$many")!!.recipients.size)
        val long = "a".repeat(10_000)
        assertEquals(ComposeIntentParser.MAX_BODY, ComposeIntentParser.parse("sms:1?body=$long")!!.body!!.length)
        assertEquals(listOf("evilscript"), ComposeIntentParser.parse("sms:<evil script>")!!.recipients)
    }

    @Test fun emptyBodyIsNull() {
        assertNull(ComposeIntentParser.parse("smsto:1?body=")!!.body)
        assertEquals(emptyList<String>(), ComposeIntentParser.parse("smsto:")!!.recipients)
    }

    @Test fun malformedPercentKept() {
        assertEquals("100%", ComposeIntentParser.parse("sms:1?body=100%")!!.body)
    }
}
