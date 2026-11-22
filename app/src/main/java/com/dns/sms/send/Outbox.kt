package com.dns.sms.send

import com.dns.sms.settings.AppSettings
import com.dns.sms.sms.ConversationRepository
import com.dns.sms.sms.Message
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends text messages. SMS only: several recipients get one separate SMS each ("Bulk").
 * Group MMS, pictures and voice notes are a planned milestone (Requirement.md).
 */
@Singleton
class Outbox @Inject constructor(
    private val sms: SmsSender,
    private val repo: ConversationRepository,
    private val settings: AppSettings,
) {
    sealed interface Result {
        data class Sent(val threadId: Long) : Result
        /** Several recipients: one separate message per recipient. */
        data class SentBulk(val count: Int) : Result
        data object Failed : Result
    }

    /** Signature (Settings > General) is added at send time, never into the draft. */
    private fun signed(text: String): String {
        val sig = settings.signature.value.trim()
        return if (sig.isEmpty() || text.isBlank()) text else "$text\n$sig"
    }

    suspend fun sendTo(recipients: List<String>, text: String, threadId: Long? = null): Result {
        if (recipients.isEmpty() || text.isBlank()) return Result.Failed
        val body = signed(text)
        if (recipients.size == 1) {
            return sms.send(recipients.single(), body, threadId)?.let { Result.Sent(it) } ?: Result.Failed
        }
        val sent = recipients.count { sms.send(it, body, null) != null }
        return if (sent > 0) Result.SentBulk(sent) else Result.Failed
    }

    suspend fun sendToThread(threadId: Long, text: String): Result {
        val thread = repo.thread(threadId) ?: return Result.Failed
        return sendTo(thread.addresses, text, threadId)
    }

    suspend fun retry(message: Message) = sms.retry(message.id)
}
