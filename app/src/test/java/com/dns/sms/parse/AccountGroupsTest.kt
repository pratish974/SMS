package com.dns.sms.parse

import com.dns.sms.data.Account
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountGroupsTest {
    private fun acc(bank: String, mask: String, at: Long, bal: String = "1") = Account(bank, mask, bal, at, at, 1)

    @Test fun sameAccountDifferentFormatsMerge() {
        val m = AccountGroups.merge(listOf(acc("HDFC", "XX1139", 10, "500"), acc("HDFC", "XXX139", 20, "700")))
        assertEquals(1, m.size)
        assertEquals("XX1139", m[0].mask)
        assertEquals(setOf("XX1139", "XXX139"), m[0].masks.toSet())
        assertEquals("700", m[0].latest.balance) // newest balance wins
    }

    @Test fun ambiguousShortEndingIsNotMerged() {
        val m = AccountGroups.merge(listOf(acc("SBI", "XX1139", 1), acc("SBI", "XX2139", 2), acc("SBI", "XXX139", 3)))
        assertEquals(3, m.size)
    }

    @Test fun differentBanksNeverMerge() {
        val m = AccountGroups.merge(listOf(acc("HDFC", "XX1139", 1), acc("ICICI", "XX1139", 2)))
        assertEquals(2, m.size)
    }

    @Test fun differentEndingsStaySeparateButSameBankCard() {
        val banks = AccountGroups.byBank(listOf(acc("HDFC", "XX1139", 5), acc("HDFC", "XX4410", 9), acc("Axis", "XX0001", 7)))
        assertEquals(listOf("HDFC", "Axis"), banks.map { it.bank })
        assertEquals(listOf("XX4410", "XX1139"), banks[0].accounts.map { it.mask })
    }

    @Test fun bankNameCaseAndSpacesIgnored() {
        val banks = AccountGroups.byBank(listOf(acc("HDFC", "XX1139", 5), acc("hdfc ", "XXX139", 6)))
        assertEquals(1, banks.size)
        assertEquals(1, banks[0].accounts.size)
    }

    @Test fun canonicalMaskReusesStoredAccount() {
        val stored = listOf(acc("HDFC", "XX1139", 1))
        assertEquals("XX1139", AccountGroups.canonicalMask("HDFC", "XXX139", stored))
        assertEquals("XX4410", AccountGroups.canonicalMask("HDFC", "XX4410", stored))
        // Stored short, new long: keep the longer one for new data.
        assertEquals("XX1139", AccountGroups.canonicalMask("HDFC", "XX1139", listOf(acc("HDFC", "XXX139", 1))))
        // Ambiguous: leave as is.
        assertEquals("XXX139", AccountGroups.canonicalMask("HDFC", "XXX139", stored + acc("HDFC", "XX2139", 2)))
    }
}
