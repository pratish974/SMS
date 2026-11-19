package com.dns.sms.classify

import com.dns.sms.classify.Category.PERSONAL
import com.dns.sms.classify.Category.PROMOTIONS
import com.dns.sms.classify.Category.TRANSACTIONS
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderResolverTest {
    private val noContacts: (String) -> Boolean = { false }

    private fun resolve(addresses: List<String>, snippet: String, overrides: Map<String, Category> = emptyMap(), contacts: (String) -> Boolean = noContacts) =
        FolderResolver.threadCategory(1, addresses, snippet, overrides, contacts)

    @Test fun rulesWhenNothingElse() {
        assertEquals(PROMOTIONS, resolve(listOf("AD-SHOPCO-P"), "hi"))
        assertEquals(TRANSACTIONS, resolve(listOf("VM-HDFCBK"), "Rs 100 debited"))
        assertEquals(PERSONAL, resolve(listOf("+919800012345"), "see you"))
    }

    @Test fun savedContactBeatsKeywords() {
        assertEquals(PERSONAL, resolve(listOf("+919800012345"), "Your OTP is 1234", contacts = { true }))
    }

    @Test fun overrideBeatsContactAndRules() {
        val o = mapOf("9800012345" to PROMOTIONS)
        assertEquals(PROMOTIONS, resolve(listOf("+91 98000 12345"), "hi", o, contacts = { true }))
    }

    @Test fun overrideAppliesAcrossDltPrefixes() {
        val o = mapOf("SHOPCO" to TRANSACTIONS)
        assertEquals(TRANSACTIONS, resolve(listOf("AD-SHOPCO-P"), "50% off sale", o))
        assertEquals(TRANSACTIONS, resolve(listOf("VM-SHOPCO"), "50% off sale", o))
    }

    @Test fun groupsArePersonalUnlessOverridden() {
        assertEquals(PERSONAL, resolve(listOf("1", "2"), "50% off sale"))
        assertEquals(PROMOTIONS, resolve(listOf("1", "2"), "x", mapOf("thread:1" to PROMOTIONS)))
    }

    @Test fun movedMessagesAddFolders() {
        assertEquals(setOf(PERSONAL, TRANSACTIONS), FolderResolver.folders(PERSONAL, listOf(TRANSACTIONS, PERSONAL)))
        assertEquals(setOf(PROMOTIONS), FolderResolver.folders(PROMOTIONS, emptyList()))
    }
}
