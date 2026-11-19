package com.dns.sms.classify

import com.dns.sms.data.MessageMetaDao
import com.dns.sms.data.SenderOverrideDao
import com.dns.sms.data.ThreadMetaDao
import com.dns.sms.sms.BlockList
import com.dns.sms.sms.ContactNames
import com.dns.sms.sms.ConversationRepository
import com.dns.sms.sms.ConversationThread
import com.dns.sms.sms.Message
import com.dns.sms.sms.messageChanges
import android.content.ContentResolver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn

data class FolderState(
    /** Visible conversations per folder: archived and blocked removed, pinned first. */
    val byCategory: Map<Category, List<ConversationThread>>,
    /** The folder each conversation belongs to (ignoring individually moved messages). */
    val threadCategory: Map<Long, Category>,
    /** Conversations whose folder the user chose ("Move To"), so "Automatic" can undo it. */
    val overridden: Set<Long>,
    val pinned: Set<Long>,
    /** Unsent text per conversation ("Draft: ..." in the list). */
    val drafts: Map<Long, String> = emptyMap(),
    val muted: Set<Long> = emptySet(),
    val archived: List<ConversationThread>,
    val blocked: List<ConversationThread>,
)

/** Live folder contents: provider threads + overrides + moved messages + contacts + pins/archive + blocks. */
@Singleton
class Folders @Inject constructor(
    private val repo: ConversationRepository,
    private val contacts: ContactNames,
    private val overrides: SenderOverrideDao,
    private val metas: MessageMetaDao,
    private val threadMetas: ThreadMetaDao,
    private val blockList: BlockList,
    private val resolver: ContentResolver,
) {
    private data class Inputs(
        val threads: List<ConversationThread>,
        val overrides: Map<String, Category>,
        val moved: Map<Long, List<Category>>,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * One shared computation for every screen (list, conversation, search, archived, blocked):
     * replays the latest state and stops 2 min after the last subscriber leaves.
     */
    private val shared: SharedFlow<FolderState> by lazy {
        // Kept alive 2 minutes after the last screen leaves, so reopening the app (or coming back
        // from another app) shows the inbox instantly instead of reloading it (perf 2026-11-22).
        build().shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 120_000), replay = 1)
    }

    fun observe(): Flow<FolderState> = shared

    private fun build(): Flow<FolderState> = combine(
        combine(repo.observeThreads(), overrides.observeAll(), metas.observeMoved()) { threads, o, moved ->
            Inputs(
                threads,
                o.associate { it.address to it.category },
                moved.groupBy { it.threadId }.mapValues { (_, v) -> v.mapNotNull { it.category } },
            )
        },
        contacts.changes(),
        threadMetas.observeAll(),
        blockList.observe(),
    ) { inputs, _, flags, blockedNumbers ->
        val started = com.dns.sms.util.Perf.now()
        val pinned = flags.filter { it.pinned }.map { it.threadId }.toSet()
        val archivedIds = flags.filter { it.archived }.map { it.threadId }.toSet()
        val isBlocked = { t: ConversationThread ->
            !t.isGroup && t.addresses.firstOrNull()?.let { blockList.matches(blockedNumbers, it) } == true
        }

        val byCategory = Category.entries.associateWith { ArrayList<ConversationThread>() }
        val threadCategory = HashMap<Long, Category>()
        val overridden = HashSet<Long>()
        val archived = ArrayList<ConversationThread>()
        val blocked = ArrayList<ConversationThread>()
        for (t in inputs.threads) {
            val cat = FolderResolver.threadCategory(t.id, t.addresses, t.snippet, inputs.overrides, contacts::isSavedContact)
            threadCategory[t.id] = cat
            if (keyFor(t) in inputs.overrides) overridden += t.id
            when {
                isBlocked(t) -> blocked += t
                t.id in archivedIds -> archived += t
                else -> FolderResolver.folders(cat, inputs.moved[t.id].orEmpty()).forEach { byCategory.getValue(it) += t }
            }
        }
        com.dns.sms.util.Perf.log("folders: ${inputs.threads.size} threads classified in ${com.dns.sms.util.Perf.now() - started} ms")
        FolderState(
            byCategory = byCategory.mapValues { (_, v) -> ThreadList.sort(v, pinned) },
            threadCategory = threadCategory,
            overridden = overridden,
            pinned = pinned,
            drafts = flags.mapNotNull { f -> f.draft?.let { f.threadId to it } }.toMap(),
            muted = flags.filter { it.muted }.map { it.threadId }.toSet(),
            archived = archived,
            blocked = blocked,
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Starred messages, newest first, [limit] at a time. Loading every starred message at once
     * built one huge "_id IN (...)" query and failed with 2 lakh stars (perf test 2026-11-21).
     */
    fun observeStarred(limit: Int): Flow<List<Message>> =
        combine(metas.observeStarredPage(limit), resolver.messageChanges()) { stars, _ ->
            com.dns.sms.util.Perf.time("starred page ($limit)") {
                stars.map { it.messageId }.chunked(500).flatMap { repo.loadByIds(it) }.sortedByDescending { it.dateMillis }
            }
        }.flowOn(Dispatchers.IO)

    fun observeStarredCount(): Flow<Int> = metas.observeStarredCount()

    companion object {
        fun keyFor(t: ConversationThread): String =
            if (t.isGroup) SenderKey.ofGroup(t.id) else SenderKey.of(t.addresses.firstOrNull().orEmpty())
    }
}
