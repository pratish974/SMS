package com.dns.sms.classify

import com.dns.sms.data.MessageMeta
import com.dns.sms.data.MessageMetaDao
import com.dns.sms.data.SenderOverride
import com.dns.sms.data.SenderOverrideDao
import com.dns.sms.sms.ConversationThread
import com.dns.sms.sms.Message
import javax.inject.Inject
import javax.inject.Singleton

/** "Move To". Sender moves are remembered for future messages; message moves affect one message. */
@Singleton
class Mover @Inject constructor(
    private val overrides: SenderOverrideDao,
    private val metas: MessageMetaDao,
) {
    /** [category] null means "Automatic": forget the user's choice. */
    suspend fun moveConversation(thread: ConversationThread, category: Category?) {
        val key = Folders.keyFor(thread)
        if (category == null) overrides.delete(key) else overrides.upsert(SenderOverride(key, category))
    }

    suspend fun moveMessage(message: Message, category: Category?) {
        val kind = message.kind.name.lowercase()
        if (category == null) {
            metas.clearCategory(kind, message.id)
            return
        }
        val existing = metas.get(kind, message.id)
        metas.upsert(existing?.copy(category = category) ?: MessageMeta(kind, message.id, message.threadId, category))
    }
}
