package com.dns.sms.classify

import com.dns.sms.sms.ConversationThread
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadListTest {
    private fun t(id: Long, date: Long, unread: Int = 0, addr: String = "a$id") =
        ConversationThread(id, listOf(addr), addr, "", date, unread)

    @Test fun pinnedFirstThenNewest() {
        val list = listOf(t(1, 100), t(2, 300), t(3, 200), t(4, 50))
        assertEquals(listOf(4L, 2L, 3L, 1L), ThreadList.sort(list, pinned = setOf(4)).map { it.id })
        assertEquals(listOf(2L, 3L, 1L, 4L), ThreadList.sort(list, pinned = emptySet()).map { it.id })
    }

    @Test fun hidesArchivedAndBlocked() {
        val list = listOf(t(1, 1), t(2, 2, addr = "SPAM"), t(3, 3))
        val shown = list.filter { ThreadList.visible(it, archived = setOf(1), blocked = { th -> th.addresses.first() == "SPAM" }) }
        assertEquals(listOf(3L), shown.map { it.id })
    }

    @Test fun unreadOnly() {
        val list = listOf(t(1, 1, unread = 2), t(2, 2))
        assertEquals(listOf(1L), ThreadList.unreadOnly(list, true).map { it.id })
        assertEquals(2, ThreadList.unreadOnly(list, false).size)
    }
}
