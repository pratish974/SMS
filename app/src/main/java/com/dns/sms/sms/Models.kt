package com.dns.sms.sms

import com.dns.sms.send.MessageStatus

data class ConversationThread(
    val id: Long,
    val addresses: List<String>,
    val title: String,
    val snippet: String,
    val dateMillis: Long,
    val unread: Int,
    /** Contact photo thumbnail URI (one-to-one conversations with a saved contact that has a photo). */
    val photo: String? = null,
) {
    val isGroup: Boolean get() = addresses.size > 1
}

/** Only SMS today; MMS is a planned milestone (Requirement.md). */
enum class MessageKind { SMS }

data class Message(
    val id: Long,
    val kind: MessageKind,
    val threadId: Long,
    val address: String?,
    val body: String,
    val subject: String?,
    val dateMillis: Long,
    val outgoing: Boolean,
    val status: MessageStatus,
    val deliveredAt: Long?,
) {
    val key: String get() = "${kind.name}-$id"
}
