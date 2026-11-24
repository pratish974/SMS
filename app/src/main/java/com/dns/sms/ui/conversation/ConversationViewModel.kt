package com.dns.sms.ui.conversation

import kotlin.time.Duration.Companion.milliseconds
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dns.sms.R
import com.dns.sms.scheduled.ScheduleLogic
import com.dns.sms.classify.Category
import com.dns.sms.classify.Folders
import com.dns.sms.classify.Mover
import com.dns.sms.data.MessageMeta
import com.dns.sms.data.ScheduledDao
import com.dns.sms.data.ScheduledMessage
import com.dns.sms.scheduled.ScheduledMessages
import com.dns.sms.data.MessageMetaDao
import com.dns.sms.sms.ConversationActions
import com.dns.sms.settings.AppSettings
import com.dns.sms.notify.MessageNotifier
import com.dns.sms.send.Outbox
import com.dns.sms.sms.ConversationRepository
import com.dns.sms.sms.ConversationThread
import com.dns.sms.sms.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConversationViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val repo: ConversationRepository,
    private val outbox: Outbox,
    private val notifier: MessageNotifier,
    private val mover: Mover,
    private val actions: ConversationActions,
    private val settings: AppSettings,
    private val scheduledMessages: ScheduledMessages,
    scheduledDao: ScheduledDao,
    folders: Folders,
    metas: MessageMetaDao,
    private val threadMetas: com.dns.sms.data.ThreadMetaDao,
    private val sims: com.dns.sms.sms.SimCards,
    private val reminders: com.dns.sms.data.ReminderDao,
    private val reminderScheduler: com.dns.sms.reminders.ReminderScheduler,
) : ViewModel() {
    val threadId: Long = checkNotNull(saved["threadId"])

    private val limit = MutableStateFlow(PAGE)
    val pageSize: StateFlow<Int> = limit.asStateFlow()

    private val threadState = MutableStateFlow<ConversationThread?>(null)
    val thread: StateFlow<ConversationThread?> = threadState.asStateFlow()

    val messages: StateFlow<List<Message>?> = limit
        .flatMapLatest { repo.observeMessages(threadId, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    data class ThreadFlags(val category: Category?, val overridden: Boolean, val archived: Boolean, val blocked: Boolean, val pinned: Boolean)

    /** This conversation's folder (and whether the user chose it), plus archive/block state. */
    val flags: StateFlow<ThreadFlags?> = folders.observe()
        .map { st ->
            ThreadFlags(
                category = st.threadCategory[threadId],
                overridden = threadId in st.overridden,
                archived = st.archived.any { it.id == threadId },
                blocked = st.blocked.any { it.id == threadId },
                pinned = threadId in st.pinned,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Per-message extras (moved folder, star), keyed by [Message.key]. */
    val messageMeta: StateFlow<Map<String, MessageMeta>> = metas.observeThread(threadId)
        .map { rows -> rows.associateBy { "${it.kind.uppercase()}-${it.messageId}" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val showQuickReplies: StateFlow<Boolean> = settings.showQuickReplies.flow
    val quickReplies: StateFlow<List<String>> = settings.quickReplies.flow

    /** Unsent text; saved (encrypted DB) so it survives leaving the conversation. */
    val draft = MutableStateFlow("")

    val muted: StateFlow<Boolean> = threadMetas.observeOne(threadId).map { it?.muted == true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    fun setMuted(muted: Boolean) {
        viewModelScope.launch {
            threadMetas.setMuted(threadId, muted)
            if (muted) notifier.cancel(threadId)
        }
    }

    // ---- Dual SIM
    val multiSim: Boolean = sims.isMultiSim()
    private val chosenSub = MutableStateFlow<Int?>(null)
    /** "SIM 1" / "SIM 2" for the composer button; null when unknown (no permission yet) or single SIM. */
    val simLabel = MutableStateFlow<String?>(null)
    fun simCards(): List<com.dns.sms.sms.SimCards.Sim> = sims.active()
    fun hasPhonePermission() = sims.hasPermission()
    fun chooseSim(subId: Int) {
        chosenSub.value = subId
        viewModelScope.launch { threadMetas.setSubId(threadId, subId); refreshSimLabel() }
    }
    fun refreshSimLabel() {
        if (!multiSim) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val sub = chosenSub.value ?: threadMetas.one(threadId)?.subId ?: repo.lastSubId(threadId)
                ?: sims.preferredSubId(settings.defaultSubId.value)
            simLabel.value = sims.shortLabel(sub)
        }
    }

    /** FEAT-1: time picked in the composer; Send then schedules instead of sending now. */
    val scheduledAt = MutableStateFlow<Long?>(null)
    val scheduled: StateFlow<List<ScheduledMessage>> = scheduledDao.observeForThread(threadId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val scheduleNotice = Channel<Long>(Channel.BUFFERED)
    fun canSendOnTime() = scheduledMessages.canSendOnTime()
    fun exactTimingIntent() = scheduledMessages.exactTimingSettingsIntent()

    fun scheduledSendNow(m: ScheduledMessage) { viewModelScope.launch { scheduledMessages.sendNow(m.id) } }
    fun scheduledChangeTime(m: ScheduledMessage, at: Long) { viewModelScope.launch { scheduledMessages.changeTime(m.id, at) } }
    fun scheduledCancel(m: ScheduledMessage) { viewModelScope.launch { scheduledMessages.cancel(m.id) } }
    /** Puts the text back in the composer with the same time, and removes the pending one. */
    fun scheduledEdit(m: ScheduledMessage) {
        draft.value = m.body
        scheduledAt.value = m.sendAt.takeIf { it > System.currentTimeMillis() }
        viewModelScope.launch { scheduledMessages.cancel(m.id) }
    }

    private val errorChannel = Channel<Int>(Channel.BUFFERED)
    val errors = errorChannel.receiveAsFlow()

    init {
        viewModelScope.launch { threadState.value = repo.thread(threadId) }
        viewModelScope.launch {
            threadMetas.one(threadId)?.draft?.let { saved -> if (draft.value.isEmpty()) draft.value = saved }
            // Save while typing (debounced); an empty box clears the draft.
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            draft.drop(1).debounce(400.milliseconds).collect { threadMetas.setDraft(threadId, it) }
        }
        refreshSimLabel()
    }

    override fun onCleared() {
        // Leaving the screen: save the latest text right away (viewModelScope is already cancelled).
        val text = draft.value
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { threadMetas.setDraft(threadId, text) }
    }

    fun loadEarlier() { limit.value += PAGE }


    fun send() {
        val text = draft.value.trim()
        if (text.isEmpty()) return
        when (ScheduleLogic.sendMode(scheduledAt.value, System.currentTimeMillis())) {
            ScheduleLogic.SendMode.TIME_PASSED -> {
                viewModelScope.launch { errorChannel.send(R.string.schedule_time_passed) }
                return
            }
            ScheduleLogic.SendMode.SCHEDULE -> {
                val at = scheduledAt.value ?: return
                val to = thread.value?.addresses ?: return
                draft.value = ""
                scheduledAt.value = null
                viewModelScope.launch {
                    scheduledMessages.schedule(to, text, at, threadId)
                    scheduleNotice.send(at)
                }
                return
            }
            ScheduleLogic.SendMode.NOW -> Unit
        }
        draft.value = ""
        viewModelScope.launch {
            when (outbox.sendToThread(threadId, text)) {
                is Outbox.Result.Sent, is Outbox.Result.SentBulk -> Unit
                Outbox.Result.Failed -> {
                    draft.value = text
                    errorChannel.send(R.string.send_failed)
                }
            }
        }
    }

    fun moveConversation(category: Category?) {
        val t = thread.value ?: return
        viewModelScope.launch { mover.moveConversation(t, category) }
    }

    /** Selection "Move to": moves the messages; with [future], new SMS from this sender go there too. */
    fun moveMessages(ms: Collection<Message>, category: Category, future: Boolean) {
        val t = thread.value
        viewModelScope.launch {
            ms.forEach { mover.moveMessage(it, category) }
            if (future && t != null) mover.moveConversation(t, category)
        }
    }

    /** Starting time for "Add Reminder": the due date found in the SMS, if any and still ahead. */
    fun suggestedReminderAt(m: Message, now: Long = System.currentTimeMillis()): Long? =
        com.dns.sms.parse.ReminderParser.parse(m.address.orEmpty(), m.body, m.dateMillis, java.time.ZoneId.systemDefault())
            ?.dueAt?.takeIf { it > now }

    /** Selection "Add Reminder": a manual reminder that links back to the message. */
    fun addReminder(m: Message, title: String, dueAt: Long) {
        viewModelScope.launch {
            reminders.upsert(
                com.dns.sms.data.Reminder(
                    kind = "MANUAL", title = title.trim().take(120), detail = null,
                    dueAt = dueAt, sourceMessageId = m.id, threadId = threadId,
                ),
            )
            reminderScheduler.reschedule()
        }
    }

    fun setStarred(m: Message, starred: Boolean) { viewModelScope.launch { actions.setStarred(m, starred) } }
    fun deleteMessage(m: Message) { viewModelScope.launch { actions.deleteMessage(m) } }
    fun setPinned(pinned: Boolean) { viewModelScope.launch { actions.setPinned(listOf(threadId), pinned) } }
    fun setArchived(archived: Boolean) { viewModelScope.launch { actions.setArchived(listOf(threadId), archived) } }
    fun block() { thread.value?.let { t -> viewModelScope.launch { actions.block(listOf(t)) } } }
    fun unblock() { thread.value?.addresses?.firstOrNull()?.let(actions::unblock) }

    fun retry(m: Message) { viewModelScope.launch { outbox.retry(m) } }

    fun markRead() {
        viewModelScope.launch {
            repo.markThreadRead(threadId)
            notifier.cancel(threadId)
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            actions.delete(listOf(threadId))
            onDone()
        }
    }

    companion object {
        const val PAGE = 300
    }
}
