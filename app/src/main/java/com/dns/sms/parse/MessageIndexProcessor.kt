package com.dns.sms.parse

import com.dns.sms.data.MessageFts
import com.dns.sms.data.MessageFtsDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feeds the full-text search index (inbox and sent). Re-indexing the same SMS just replaces its row,
 * so it is idempotent.
 */
@Singleton
class MessageIndexProcessor @Inject constructor(private val dao: MessageFtsDao) : MessageProcessor {
    override val version: Int = 1
    override val inboxOnly: Boolean = false

    override suspend fun process(sms: ScannedSms) = dao.insertAll(listOf(row(sms)))

    override suspend fun processBatch(batch: List<ScannedSms>) = dao.insertAll(batch.map(::row))

    override suspend fun clear() = dao.clear()

    private fun row(s: ScannedSms) = MessageFts(s.id, s.body, s.address, s.threadId, s.date)
}
