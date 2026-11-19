package com.dns.sms.classify

import com.dns.sms.sms.ConversationThread

/** Pure list rules for the conversation list, so they are unit tested. */
object ThreadList {
    /** Pinned first, then newest first. */
    fun sort(threads: List<ConversationThread>, pinned: Set<Long>): List<ConversationThread> =
        threads.sortedWith(compareByDescending<ConversationThread> { it.id in pinned }.thenByDescending { it.dateMillis })

    fun visible(t: ConversationThread, archived: Set<Long>, blocked: (ConversationThread) -> Boolean): Boolean =
        t.id !in archived && !blocked(t)

    fun unreadOnly(threads: List<ConversationThread>, enabled: Boolean): List<ConversationThread> =
        if (enabled) threads.filter { it.unread > 0 } else threads
}
