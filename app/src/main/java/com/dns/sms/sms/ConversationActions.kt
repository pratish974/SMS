package com.dns.sms.sms

import com.dns.sms.data.MessageMeta
import com.dns.sms.data.MessageMetaDao
import com.dns.sms.data.ThreadMeta
import com.dns.sms.data.ThreadMetaDao
import android.content.Context
import com.dns.sms.notify.MessageNotifier
import com.dns.sms.work.SmsScanWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Conversation and message actions from multi-select, swipes and menus. */
@Singleton
class ConversationActions @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
    private val repo: ConversationRepository,
    private val threadMeta: ThreadMetaDao,
    private val messageMeta: MessageMetaDao,
    private val notifier: MessageNotifier,
    private val blockList: BlockList,
    private val shortcuts: com.dns.sms.notify.ConversationShortcuts,
) {
    suspend fun setPinned(ids: Collection<Long>, pinned: Boolean) = updateFlags(ids) { it.copy(pinned = pinned) }

    suspend fun setArchived(ids: Collection<Long>, archived: Boolean) = updateFlags(ids) { it.copy(archived = archived) }

    suspend fun markRead(ids: Collection<Long>) {
        repo.markThreadsRead(ids)
        ids.forEach(notifier::cancel)
    }

    suspend fun delete(ids: Collection<Long>) {
        val list = ids.toList()
        repo.deleteThreads(list)
        messageMeta.deleteThreads(list)
        threadMeta.delete(list)
        list.forEach(notifier::cancel)
        shortcuts.remove(list)
    }

    /** Blocks the sender of each one-to-one conversation (groups have no single sender). */
    fun block(threads: Collection<ConversationThread>) {
        threads.filter { !it.isGroup }.forEach { t ->
            t.addresses.firstOrNull()?.let(blockList::block)
            notifier.cancel(t.id)
        }
    }

    fun unblock(address: String) = blockList.unblock(address)

    suspend fun setStarred(m: Message, starred: Boolean) {
        val kind = m.kind.name.lowercase()
        val existing = messageMeta.get(kind, m.id)
        messageMeta.upsert(existing?.copy(starred = starred) ?: MessageMeta(kind, m.id, m.threadId, starred = starred))
    }

    suspend fun deleteMessage(m: Message) {
        repo.deleteMessage(m.id)
        messageMeta.delete(m.kind.name.lowercase(), m.id)
    }

    /**
     * Called for every newly stored incoming message. Blocked senders are stored silently;
     * an archived conversation comes back when a new message arrives.
     */
    suspend fun onIncoming(threadId: Long, sender: String?) {
        SmsScanWorker.enqueue(ctx)
        if (sender != null && blockList.isBlocked(sender)) return
        setArchived(listOf(threadId), false)
        notifier.notifyThread(threadId)
    }

    private suspend fun updateFlags(ids: Collection<Long>, change: (ThreadMeta) -> ThreadMeta) {
        if (ids.isEmpty()) return
        val existing = threadMeta.get(ids.toList()).associateBy { it.threadId }
        threadMeta.upsertAll(ids.map { change(existing[it] ?: ThreadMeta(it)) })
    }
}
