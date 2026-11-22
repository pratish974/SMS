package com.dns.sms.parse

import com.dns.sms.classify.OtpDetector
import com.dns.sms.data.Account
import com.dns.sms.data.FinanceDao
import com.dns.sms.data.FinanceTxn
import com.dns.sms.sms.PhoneNumbers
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the latest balance per account and a list of debits/credits. The accounts/masks known per bank
 * are cached during a scan (and extended as new masks are stored) instead of two queries per SMS.
 */
@Singleton
class FinanceProcessor @Inject constructor(private val dao: FinanceDao) : MessageProcessor {
    override val version: Int = FinanceParser.VERSION
    private val knownByBank = HashMap<String, MutableList<Account>>()

    override suspend fun onScanStart() = knownByBank.clear()

    override suspend fun process(sms: ScannedSms) {
        // Banks send from alphanumeric ids; a person's number is never a bank account update.
        if (PhoneNumbers.isPhoneLike(sms.address) && !Banks.isKnown(sms.address)) return
        if (!FinanceParser.mightMatch(sms.body)) return
        if (OtpDetector.isOtp(sms.body)) return
        val parsed = FinanceParser.parse(sms.address, sms.body) ?: return
        // Same bank + same account ending in a different format ("XXX139" vs "XX1139") -> same account.
        val known = knownByBank.getOrPut(parsed.bank) {
            (dao.accountsOf(parsed.bank) + dao.txnMasksOf(parsed.bank).map { Account(parsed.bank, it, null, 0, 0, 0) }).toMutableList()
        }
        val u = parsed.copy(mask = AccountGroups.canonicalMask(parsed.bank, parsed.mask, known))
        if (known.none { it.mask == u.mask }) known += Account(u.bank, u.mask, null, 0, 0, 0)
        if (u.balance != null) {
            val existing = dao.account(u.bank, u.mask)
            if (existing == null || existing.balanceAt <= sms.date) {
                dao.upsertAccount(Account(u.bank, u.mask, u.balance.toPlainString(), sms.date, sms.id, FinanceParser.VERSION))
            }
        }
        if (u.txnType != null && u.txnAmount != null) {
            dao.upsertTxn(FinanceTxn(sms.id, u.bank, u.mask, u.txnType, u.txnAmount.toPlainString(), sms.date))
        }
    }

    override suspend fun clear() {
        dao.clearAccounts()
        dao.clearTxns()
        knownByBank.clear()
    }
}
