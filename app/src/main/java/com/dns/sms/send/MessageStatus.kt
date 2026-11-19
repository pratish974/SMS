package com.dns.sms.send

/** What the UI shows under a message. */
enum class MessageStatus { RECEIVED, SENDING, SENT, DELIVERED, FAILED }

/** Maps provider columns to [MessageStatus]. Pure, so it is unit tested. */
object StatusMapper {
    // Telephony.Sms type / status values.
    const val SMS_INBOX = 1
    const val SMS_SENT = 2
    const val SMS_OUTBOX = 4
    const val SMS_FAILED = 5
    const val SMS_QUEUED = 6
    const val SMS_STATUS_COMPLETE = 0
    const val SMS_STATUS_FAILED = 64

    fun sms(type: Int, status: Int, delivered: Boolean): MessageStatus = when (type) {
        SMS_INBOX -> MessageStatus.RECEIVED
        SMS_OUTBOX, SMS_QUEUED -> MessageStatus.SENDING
        SMS_FAILED -> MessageStatus.FAILED
        SMS_SENT -> when {
            status == SMS_STATUS_FAILED -> MessageStatus.FAILED
            status == SMS_STATUS_COMPLETE || delivered -> MessageStatus.DELIVERED
            else -> MessageStatus.SENT
        }
        else -> MessageStatus.RECEIVED
    }
}
