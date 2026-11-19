package com.dns.sms.ui.conversation

import com.dns.sms.classify.Category

/**
 * Rules for selecting messages inside a conversation (reference screens, Nov 2026).
 * Plain Kotlin so it can be unit-tested; the screen only holds the [Set] of message keys.
 *
 * Long-press a bubble starts selecting; while selecting, a tap toggles. The bar shows
 * Select all, Delete, Copy, Star and an overflow whose items depend on how many are selected.
 */
object MessageSelection {

    /** Overflow items, in display order. */
    enum class MenuItem { SHARE, MOVE, ADD_REMINDER, FORWARD, DETAILS }

    fun toggle(selected: Set<String>, key: String): Set<String> =
        if (key in selected) selected - key else selected + key

    /** Select all, or clear when everything is already selected (the Select all icon toggles). */
    fun selectAll(selected: Set<String>, all: Collection<String>): Set<String> =
        if (all.isNotEmpty() && selected.containsAll(all)) emptySet() else all.toSet()

    /** Drops keys of messages that no longer exist (deleted here or elsewhere). Same instance when nothing changed. */
    fun retain(selected: Set<String>, live: Set<String>): Set<String> =
        if (live.containsAll(selected)) selected else selected.filterTo(LinkedHashSet()) { it in live }

    /** Forward works on one message only; everything else works on any number. */
    fun menu(count: Int): List<MenuItem> = when {
        count <= 0 -> emptyList()
        count == 1 -> listOf(MenuItem.SHARE, MenuItem.MOVE, MenuItem.ADD_REMINDER, MenuItem.FORWARD, MenuItem.DETAILS)
        else -> listOf(MenuItem.SHARE, MenuItem.MOVE, MenuItem.ADD_REMINDER, MenuItem.DETAILS)
    }

    /** Folders offered by Move: every folder except the one all selected messages are already in. */
    fun moveTargets(currentOfEach: Collection<Category>): List<Category> {
        val current = currentOfEach.toSet().singleOrNull()
        return Category.entries.filter { it != current }
    }

    /** Star unless every selected message is already starred, then unstar. */
    fun shouldStar(starredFlags: Collection<Boolean>): Boolean = !(starredFlags.isNotEmpty() && starredFlags.all { it })

    /** Text for Copy / Share / Forward: oldest first, separated by a blank line. */
    fun <T> joinText(items: Collection<T>, date: (T) -> Long, body: (T) -> String): String =
        items.sortedBy(date).joinToString("\n\n", transform = body)
}
