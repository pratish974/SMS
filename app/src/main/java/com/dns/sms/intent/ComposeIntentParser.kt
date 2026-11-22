package com.dns.sms.intent

import com.dns.sms.sms.PhoneNumbers
import java.io.ByteArrayOutputStream

/**
 * Parses sms:/smsto:/mms:/mmsto: URIs from other apps. Input is untrusted:
 * recipients and body are capped and sanitized.
 */
object ComposeIntentParser {
    const val MAX_RECIPIENTS = 20
    const val MAX_BODY = 5000
    private val schemes = setOf("sms", "smsto", "mms", "mmsto")

    data class Draft(val recipients: List<String>, val body: String?)

    fun parse(uri: String?): Draft? {
        if (uri == null) return null
        val colon = uri.indexOf(':')
        if (colon <= 0) return null
        val scheme = uri.substring(0, colon).lowercase()
        if (scheme !in schemes) return null
        var rest = uri.substring(colon + 1).removePrefix("//")
        var body: String? = null
        val q = rest.indexOf('?')
        if (q >= 0) {
            body = rest.substring(q + 1).split('&')
                .firstOrNull { it.startsWith("body=", ignoreCase = true) }
                ?.substringAfter('=')
                ?.let(::percentDecode)
            rest = rest.substring(0, q)
        }
        return Draft(recipients(percentDecode(rest)), cleanBody(body))
    }

    fun recipients(raw: String): List<String> = raw.split(',', ';')
        .map { sanitize(it) }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_RECIPIENTS)

    fun cleanBody(body: String?): String? = body?.take(MAX_BODY)?.takeIf { it.isNotEmpty() }

    private fun sanitize(raw: String): String {
        val t = raw.trim().take(64)
        if (PhoneNumbers.isPhoneLike(t)) return PhoneNumbers.normalize(t)
        // Short codes / emails for MMS: allow a conservative character set only.
        return t.filter { it.isLetterOrDigit() || it in "+@._-" }
    }

    /** RFC 3986 percent-decoding. Unlike URLDecoder, '+' stays '+' (it starts phone numbers). */
    fun percentDecode(s: String): String {
        if ('%' !in s) return s
        val out = ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                val v = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (v != null) { out.write(v); i += 3; continue }
            }
            out.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
