package com.dns.sms.parse

import com.dns.sms.data.Account

/**
 * Groups Finance accounts (Requirement.md section 4, Finance):
 * 1. **Same account, different SMS formats.** Banks print the ending differently ("XX1139", "XXX139",
 *    "A/c *1139"). Accounts of the same bank whose digits match at the end (one is a suffix of the
 *    other, at least 3 digits) are one account, but only when that match is unique: "XXX139" is not
 *    merged if both "XX1139" and "XX2139" exist.
 * 2. **Same bank.** All accounts of one bank are listed together under that bank.
 * Pure, so it is unit tested.
 */
object AccountGroups {

    /** One real account, possibly reported under several masks. */
    data class Merged(
        val bank: String,
        /** The most complete mask (most digits), shown in the list. */
        val mask: String,
        /** Every mask seen for this account (used to load all its transactions). */
        val masks: List<String>,
        /** The newest balance of all its masks. */
        val latest: Account,
    )

    data class Bank(val bank: String, val accounts: List<Merged>) {
        val latestAt: Long get() = accounts.maxOf { it.latest.balanceAt }
    }

    fun digits(mask: String) = mask.filter { it.isDigit() }

    private fun bankKey(bank: String) = bank.trim().lowercase()

    /** True when two endings can be the same account ("1139" and "139"); needs at least 3 digits. */
    fun sameEnding(a: String, b: String): Boolean {
        val (long, short) = if (a.length >= b.length) a to b else b to a
        return short.length >= 3 && long.endsWith(short)
    }

    fun merge(accounts: List<Account>): List<Merged> =
        accounts.groupBy { bankKey(it.bank) }.values.flatMap { mergeBank(it) }

    private fun mergeBank(list: List<Account>): List<Merged> {
        // Longest endings first: they become the anchors shorter endings join.
        val sorted = list.sortedWith(compareByDescending<Account> { digits(it.mask).length }.thenByDescending { it.balanceAt })
        val groups = mutableListOf<MutableList<Account>>()
        for (a in sorted) {
            val d = digits(a.mask)
            val matches = groups.filter { g -> sameEnding(digits(g.first().mask), d) }
            if (matches.size == 1) matches[0] += a else groups += mutableListOf(a)
        }
        return groups.map { g ->
            Merged(
                bank = g.first().bank,
                mask = g.first().mask,
                masks = g.map { it.mask }.distinct(),
                latest = g.maxBy { it.balanceAt },
            )
        }
    }

    /** Banks ordered by their most recent update; accounts inside a bank likewise. */
    fun byBank(accounts: List<Account>): List<Bank> =
        merge(accounts).groupBy { bankKey(it.bank) }.values
            .map { Bank(it.first().bank, it.sortedByDescending { m -> m.latest.balanceAt }) }
            .sortedByDescending { it.latestAt }

    /**
     * For storing a new SMS: if an account of this bank already exists under a longer or shorter mask
     * with the same unique ending, reuse the stored mask so new data lands on the same account.
     */
    fun canonicalMask(bank: String, mask: String, existing: List<Account>): String {
        val d = digits(mask)
        val candidates = existing.filter { bankKey(it.bank) == bankKey(bank) && sameEnding(digits(it.mask), d) }
        if (candidates.any { it.mask == mask }) return mask
        val distinctEndings = candidates.map { digits(it.mask) }.distinct()
        if (distinctEndings.size != 1) return mask
        // Keep the more complete of the two.
        val stored = candidates.first().mask
        return if (digits(stored).length >= d.length) stored else mask
    }
}
