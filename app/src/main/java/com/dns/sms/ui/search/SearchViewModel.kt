package com.dns.sms.ui.search

import kotlin.time.Duration.Companion.milliseconds
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dns.sms.classify.Folders
import com.dns.sms.sms.ConversationRepository
import com.dns.sms.sms.ConversationThread
import com.dns.sms.sms.Message
import com.dns.sms.sms.SearchQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class SearchResults(val query: String, val threads: List<ConversationThread>, val messages: List<Message>)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    folders: Folders,
    private val repo: ConversationRepository,
) : ViewModel() {
    val query = MutableStateFlow("")

    /**
     * The message search depends only on the query, so it is not re-run when folder state changes;
     * folders only feed the cheap filtering (blocked threads, titles), done off the main thread.
     * Blocked conversations are left out of search, archived ones are included.
     */
    private val hits = query.debounce(250.milliseconds).map { SearchQuery.normalize(it) }.distinctUntilChanged()
        .mapLatest { q -> q?.let { it to repo.search(it) } }

    val results: StateFlow<SearchResults?> = combine(hits, folders.observe()) { hit, st ->
        val (q, found) = hit ?: return@combine null
        val blocked = st.blocked.map { it.id }.toSet()
        val visible = (st.byCategory.values.flatten() + st.archived).distinctBy { it.id }
        val threads = visible.filter { t ->
            t.title.contains(q, ignoreCase = true) || t.addresses.any { it.contains(q, ignoreCase = true) }
        }
        SearchResults(q, threads, found.filter { it.threadId !in blocked })
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun titleFor(threadId: Long, threads: List<ConversationThread>): String? = threads.firstOrNull { it.id == threadId }?.title
}
