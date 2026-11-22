package com.dns.sms.parse

import com.dns.sms.classify.OtpDetector
import com.dns.sms.data.OrderDao
import com.dns.sms.data.OrderEvent
import com.dns.sms.data.Reminder
import com.dns.sms.data.ReminderDao
import com.dns.sms.data.ShopOrder
import com.dns.sms.reminders.ReminderScheduler
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderProcessor @Inject constructor(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
) : MessageProcessor {
    override val version: Int = ReminderParser.VERSION

    private var dirty = false

    override suspend fun process(sms: ScannedSms) {
        if (!ReminderParser.mightMatch(sms.body)) return
        if (OtpDetector.isOtp(sms.body)) return
        val p = ReminderParser.parse(sms.address, sms.body, sms.date, ZoneId.systemDefault()) ?: return
        // The first scan covers years of SMS: long-past bills and trips would only clutter Past reminders.
        if (p.dueAt < System.currentTimeMillis() - PAST_LIMIT_MS) return
        // Keep the user's "dismissed" across rescans (same SMS + kind is the same reminder).
        val existing = dao.forMessage(sms.id, p.kind)
        dao.upsert(
            Reminder(
                id = existing?.id ?: 0, kind = p.kind, title = p.title, detail = p.detail, dueAt = p.dueAt,
                sourceMessageId = sms.id, threadId = sms.threadId, dismissed = existing?.dismissed ?: false,
            ),
        )
        dirty = true
    }

    /** One alarm update per batch instead of one per reminder. */
    override suspend fun onBatchDone() {
        if (!dirty) return
        dirty = false
        scheduler.reschedule()
    }

    private companion object { const val PAST_LIMIT_MS = 30L * 24 * 60 * 60 * 1000 }

    /** Parsed reminders are rebuilt on rescan, but dismissed ones stay so they never come back. */
    override suspend fun clear() = dao.clearParsedActive()
}

@Singleton
class OrderProcessor @Inject constructor(private val dao: OrderDao) : MessageProcessor {
    override val version: Int = OrderParser.VERSION

    override suspend fun process(sms: ScannedSms) {
        val p = OrderParser.parse(sms.address, sms.body, sms.date, ZoneId.systemDefault()) ?: return
        val existing = dao.get(p.key)
        if (existing == null || existing.statusAt <= sms.date) {
            dao.upsert(
                ShopOrder(
                    key = p.key, merchant = p.merchant, title = p.title, orderId = p.orderId ?: existing?.orderId,
                    status = p.status, statusAt = sms.date, url = p.url ?: existing?.url,
                    expectedAt = p.expectedAt ?: existing?.expectedAt, threadId = sms.threadId,
                ),
            )
        }
        dao.upsertEvent(OrderEvent(p.key, sms.id, p.status, sms.date))
    }

    override suspend fun clear() {
        dao.clearOrders()
        dao.clearEvents()
    }
}
