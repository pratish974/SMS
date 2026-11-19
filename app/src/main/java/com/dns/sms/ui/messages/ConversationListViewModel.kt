package com.dns.sms.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dns.sms.classify.Category
import com.dns.sms.classify.FolderState
import com.dns.sms.classify.Folders
import com.dns.sms.classify.Mover
import com.dns.sms.sms.ConversationActions
import com.dns.sms.settings.AppSettings
import com.dns.sms.sms.ConversationThread
import com.dns.sms.sms.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** Shared by the list and AppRoot's selection bar (same nav entry, same instance). */
@HiltViewModel
class ConversationListViewModel @Inject constructor(
    folders: Folders,
    private val mover: Mover,
    private val actions: ConversationActions,
    settings: AppSettings,
    private val threadMetas: com.dns.sms.data.ThreadMetaDao,
    private val notifier: com.dns.sms.notify.MessageNotifier,
) : ViewModel() {
    val leftSwipe = settings.leftSwipe.flow
    val rightSwipe = settings.rightSwipe.flow
    val changeTabsOnSwipe = settings.changeTabsOnSwipe.flow


    val state: StateFlow<FolderState?> = folders.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val starredLimit = MutableStateFlow(STARRED_PAGE)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val starred: StateFlow<List<Message>?> = starredLimit.flatMapLatest { folders.observeStarred(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val starredCount: StateFlow<Int> = folders.observeStarredCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    fun loadMoreStarred() { starredLimit.value += STARRED_PAGE }

    val selection = MutableStateFlow<Set<Long>>(emptySet())
    val unreadOnly = MutableStateFlow(false)

    fun toggle(id: Long) { selection.value = selection.value.let { if (id in it) it - id else it + id } }
    fun selectAll(ids: Collection<Long>) { selection.value = ids.toSet() }
    fun clearSelection() { selection.value = emptySet() }

    fun selectedThreads(): List<ConversationThread> {
        val st = state.value ?: return emptyList()
        val ids = selection.value
        return (st.byCategory.values.flatten() + st.archived + st.blocked).filter { it.id in ids }.distinctBy { it.id }
    }

    /** Pins unless every selected conversation is already pinned, then unpins. */
    fun togglePinSelected() = act { ids ->
        val pinned = state.value?.pinned.orEmpty()
        actions.setPinned(ids, pinned = !ids.all { it in pinned })
    }

    fun markRead(ids: Collection<Long>) = launch { actions.markRead(ids) }

    /** Mutes unless every selected conversation is already muted, then unmutes. */
    fun toggleMuteSelected() = act { ids ->
        val muted = state.value?.muted.orEmpty()
        val mute = !ids.all { it in muted }
        ids.forEach { id ->
            threadMetas.setMuted(id, mute)
            if (mute) notifier.cancel(id)
        }
    }

    /** Every unread conversation in this folder (Starred: the conversations of starred messages). */
    fun markAllRead(folder: com.dns.sms.ui.Folder) = launch {
        val st = state.value ?: return@launch
        val ids = when (val c = folder.category) {
            null -> starred.value.orEmpty().map { it.threadId }.toSet()
            else -> st.byCategory[c].orEmpty().filter { it.unread > 0 }.map { it.id }.toSet()
        }
        if (ids.isNotEmpty()) actions.markRead(ids)
    }
    fun archive(ids: Collection<Long>, archived: Boolean) = launch { actions.setArchived(ids, archived) }
    fun delete(ids: Collection<Long>) = launch { actions.delete(ids) }
    fun block(threads: Collection<ConversationThread>) = launch { actions.block(threads) }
    fun unblock(thread: ConversationThread) = launch { thread.addresses.firstOrNull()?.let(actions::unblock) }
    fun move(threads: Collection<ConversationThread>, category: Category?) =
        launch { threads.forEach { mover.moveConversation(it, category) } }
    fun unstar(m: Message) = launch { actions.setStarred(m, false) }

    /** Runs [block] on the current selection, then leaves selection mode. */
    fun act(block: suspend (Set<Long>) -> Unit) {
        val ids = selection.value
        if (ids.isEmpty()) return
        clearSelection()
        viewModelScope.launch { block(ids) }
    }

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    private companion object { const val STARRED_PAGE = 300 }
}
