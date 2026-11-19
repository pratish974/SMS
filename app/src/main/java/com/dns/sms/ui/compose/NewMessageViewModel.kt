package com.dns.sms.ui.compose

import kotlin.time.Duration.Companion.milliseconds
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dns.sms.R
import com.dns.sms.scheduled.ScheduleLogic
import com.dns.sms.intent.ComposeIntentParser
import com.dns.sms.send.Outbox
import com.dns.sms.sms.ContactHit
import com.dns.sms.sms.ContactMatch
import com.dns.sms.sms.ContactNames
import com.dns.sms.sms.ContactSearch
import com.dns.sms.sms.ConversationRepository
import com.dns.sms.sms.Message
import com.dns.sms.sms.PhoneNumbers
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Recipient(val name: String?, val number: String, val photo: Uri?) {
    val label: String get() = name ?: number
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewMessageViewModel @Inject constructor(
    saved: SavedStateHandle,
    private val outbox: Outbox,
    private val search: ContactSearch,
    private val repo: ConversationRepository,
    private val contacts: ContactNames,
    private val scheduledMessages: com.dns.sms.scheduled.ScheduledMessages,
) : ViewModel() {
    val scheduledAt = MutableStateFlow<Long?>(null)
    fun canSendOnTime() = scheduledMessages.canSendOnTime()
    fun exactTimingIntent() = scheduledMessages.exactTimingSettingsIntent()

    val recipients = MutableStateFlow(
        ComposeIntentParser.recipients(saved.get<String>("to").orEmpty()).map { Recipient(contacts.nameFor(it), it, null) },
    )
    /** Opened by another app (sms: link or share) with a recipient or text already filled in. */
    val filledByOtherApp = saved.get<String>("ext") == "1" && recipients.value.isNotEmpty() ||
        saved.get<String>("ext") == "1" && !ComposeIntentParser.cleanBody(saved.get<String>("body")).isNullOrEmpty()
    val query = MutableStateFlow("")
    val body = MutableStateFlow(ComposeIntentParser.cleanBody(saved.get<String>("body")).orEmpty())
    val sending = MutableStateFlow(false)
    val hasContactsPermission = MutableStateFlow(search.hasPermission())

    /** Saved contacts first, then recent conversations; refreshed as you type. */
    val results: StateFlow<List<ContactHit>> = query.debounce(150.milliseconds).mapLatest { search.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recents: StateFlow<List<ContactHit>> = flow { emit(search.recents()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** With exactly one recipient you've messaged before, the existing conversation is shown above the composer. */
    val preview: StateFlow<List<Message>?> = recipients.flatMapLatest { r ->
        val one = r.singleOrNull() ?: return@flatMapLatest flowOf(null)
        val threadId = withContext(Dispatchers.IO) {
            repo.loadThreads().firstOrNull { !it.isGroup && it.addresses.any { a -> PhoneNumbers.same(a, one.number) } }?.id
        }
        if (threadId == null) flowOf(null) else repo.observeMessages(threadId, 100)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    sealed interface Event {
        data class Sent(val threadId: Long) : Event
        data object SentBulk : Event
        data class Error(val message: Int) : Event
        data object RecipientAdded : Event
        data class Scheduled(val at: Long) : Event
    }
    private val eventChannel = Channel<Event>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    fun permissionChanged() {
        hasContactsPermission.value = search.hasPermission()
        contacts.invalidate()
        query.value = query.value // re-run the search
    }

    fun add(hit: ContactHit) = addRecipient(Recipient(hit.name, hit.number, hit.photo))

    /** "Send to <typed number>" or the keyboard's Done key. */
    fun addTyped(): Boolean {
        val n = ContactMatch.asNumber(query.value) ?: return false
        addRecipient(Recipient(contacts.nameFor(n), n, contacts.photoFor(n)))
        return true
    }

    private fun addRecipient(r: Recipient) {
        if (recipients.value.none { PhoneNumbers.same(it.number, r.number) }) {
            recipients.value = (recipients.value + r).take(ComposeIntentParser.MAX_RECIPIENTS)
        }
        query.value = ""
        viewModelScope.launch { eventChannel.send(Event.RecipientAdded) }
    }

    fun remove(r: Recipient) { recipients.value = recipients.value - r }


    fun send() {
        if (sending.value) return
        // A number typed but not yet turned into a chip still counts.
        ContactMatch.asNumber(query.value)?.let { addTyped() }
        val to = recipients.value.map { it.number }
        if (to.isEmpty()) return
        val mode = ScheduleLogic.sendMode(scheduledAt.value, System.currentTimeMillis())
        if (mode == ScheduleLogic.SendMode.TIME_PASSED) {
            viewModelScope.launch { eventChannel.send(Event.Error(R.string.schedule_time_passed)) }
            return
        }
        if (mode == ScheduleLogic.SendMode.SCHEDULE) scheduledAt.value?.let { at ->
            val text = body.value.trim()
            if (text.isEmpty()) return
            sending.value = true
            viewModelScope.launch {
                val threadId = withContext(Dispatchers.IO) {
                    to.singleOrNull()?.let { n -> repo.loadThreads().firstOrNull { !it.isGroup && it.addresses.any { a -> PhoneNumbers.same(a, n) } }?.id }
                }
                scheduledMessages.schedule(to, text, at, threadId)
                sending.value = false
                eventChannel.send(Event.Scheduled(at))
            }
            return
        }
        sending.value = true
        viewModelScope.launch {
            val result = outbox.sendTo(to, body.value.trim())
            sending.value = false
            eventChannel.send(
                when (result) {
                    is Outbox.Result.Sent -> Event.Sent(result.threadId)
                    is Outbox.Result.SentBulk -> Event.SentBulk
                    Outbox.Result.Failed -> Event.Error(R.string.send_failed)
                },
            )
        }
    }
}
