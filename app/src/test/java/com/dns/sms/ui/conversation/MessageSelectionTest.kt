package com.dns.sms.ui.conversation

import com.dns.sms.classify.Category
import com.dns.sms.ui.conversation.MessageSelection.MenuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSelectionTest {

    @Test fun `long press then taps add and remove`() {
        var s = MessageSelection.toggle(emptySet(), "SMS-1")
        assertEquals(setOf("SMS-1"), s)
        s = MessageSelection.toggle(s, "SMS-2")
        assertEquals(setOf("SMS-1", "SMS-2"), s)
        s = MessageSelection.toggle(s, "SMS-1")
        assertEquals(setOf("SMS-2"), s)
        s = MessageSelection.toggle(s, "SMS-2")
        assertTrue("last one removed ends selection", s.isEmpty())
    }

    @Test fun `select all selects everything then clears`() {
        val all = listOf("SMS-1", "SMS-2", "MMS-3")
        val selected = MessageSelection.selectAll(setOf("SMS-1"), all)
        val expected = all.toSet()
        assertEquals(expected, selected)
        assertTrue(MessageSelection.selectAll(selected, all).isEmpty())
    }

    @Test fun `select all on empty conversation stays empty`() {
        assertTrue(MessageSelection.selectAll(emptySet(), emptyList()).isEmpty())
    }

    @Test fun `deleted messages drop out of the selection`() {
        val s = setOf("SMS-1", "SMS-2")
        assertEquals(setOf("SMS-2"), MessageSelection.retain(s, setOf("SMS-2", "SMS-3")))
        assertSame("unchanged selection keeps identity (no recomposition)", s, MessageSelection.retain(s, setOf("SMS-1", "SMS-2", "SMS-9")))
    }

    @Test fun `menu for one message has forward`() {
        assertEquals(
            listOf(MenuItem.SHARE, MenuItem.MOVE, MenuItem.ADD_REMINDER, MenuItem.FORWARD, MenuItem.DETAILS),
            MessageSelection.menu(1),
        )
    }

    @Test fun `menu for several messages has no forward`() {
        assertEquals(listOf(MenuItem.SHARE, MenuItem.MOVE, MenuItem.ADD_REMINDER, MenuItem.DETAILS), MessageSelection.menu(2))
        assertFalse(MenuItem.FORWARD in MessageSelection.menu(10))
        assertTrue(MessageSelection.menu(0).isEmpty())
    }

    @Test fun `move offers the other folders`() {
        assertEquals(listOf(Category.PERSONAL, Category.PROMOTIONS), MessageSelection.moveTargets(listOf(Category.TRANSACTIONS, Category.TRANSACTIONS)))
    }

    @Test fun `move offers every folder when messages are in different folders`() {
        val everyFolder = Category.entries.toList()
        val mixed = MessageSelection.moveTargets(listOf(Category.TRANSACTIONS, Category.PERSONAL))
        val forEmpty = MessageSelection.moveTargets(emptyList())
        assertEquals(everyFolder, mixed)
        assertEquals(everyFolder, forEmpty)
    }

    @Test fun `star unless all are starred`() {
        assertTrue(MessageSelection.shouldStar(listOf(true, false)))
        assertTrue(MessageSelection.shouldStar(listOf(false)))
        assertFalse(MessageSelection.shouldStar(listOf(true, true)))
    }

    @Test fun `copied text is oldest first with blank lines`() {
        data class M(val at: Long, val body: String)
        val text = MessageSelection.joinText(listOf(M(30, "third"), M(10, "first"), M(20, "second")), { it.at }, { it.body })
        assertEquals("first\n\nsecond\n\nthird", text)
    }
}
