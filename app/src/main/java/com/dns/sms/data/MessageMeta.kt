package com.dns.sms.data

import androidx.room.Entity
import androidx.room.Index
import com.dns.sms.classify.Category

/**
 * App-side extras for one message. The Telephony provider stays the source of truth for the message
 * itself; this holds only what the app adds. Never store message bodies here.
 *
 * [kind] is "sms" or "mms": the provider keeps them in separate tables, so ids alone can collide.
 * [category] is set when the user moved just this message ("Move To"); null means "follow the thread".
 */
@Entity(
    tableName = "message_meta",
    primaryKeys = ["kind", "messageId"],
    indices = [Index("threadId"), Index("category")],
)
data class MessageMeta(
    val kind: String,
    val messageId: Long,
    val threadId: Long,
    val category: Category? = null,
    val pinned: Boolean = false,
    val starred: Boolean = false,
)
