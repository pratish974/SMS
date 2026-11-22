package com.dns.sms.ui.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dns.sms.data.OrderDao
import com.dns.sms.data.OrderEvent
import com.dns.sms.data.Reminder
import com.dns.sms.data.ReminderDao
import com.dns.sms.data.ShopOrder
import com.dns.sms.parse.SmsScanner
import com.dns.sms.reminders.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Emits "now" every minute so upcoming items move to Past without a restart. */
private fun minuteTicker(): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(60_000)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val reminders: ReminderDao,
    orders: OrderDao,
    private val scheduler: ReminderScheduler,
    scanner: SmsScanner,
) : ViewModel() {
    val upcoming: StateFlow<List<Reminder>?> = minuteTicker().flatMapLatest { reminders.observeUpcoming(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val past: StateFlow<List<Reminder>> = minuteTicker().flatMapLatest { reminders.observePast(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activeOrders: StateFlow<List<ShopOrder>> = minuteTicker().flatMapLatest { orders.observeActive(it - ACTIVE_ORDER_WINDOW_MS) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allOrders: StateFlow<List<ShopOrder>> = orders.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** False until the first inbox scan finishes: shows "being set up" instead of "no reminders". */
    val ready = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            ready.value = scanner.hasCompletedOnce()
            while (!ready.value) { delay(2_000); ready.value = scanner.hasCompletedOnce() }
        }
    }

    private companion object { const val ACTIVE_ORDER_WINDOW_MS = 21L * 24 * 60 * 60 * 1000 }

    fun dismiss(r: Reminder) = viewModelScope.launch {
        reminders.dismiss(r.id)
        scheduler.reschedule()
    }

    fun delete(r: Reminder) = viewModelScope.launch {
        reminders.delete(r.id)
        scheduler.reschedule()
    }

    fun addManual(title: String, dueAt: Long) = viewModelScope.launch {
        reminders.upsert(Reminder(kind = "MANUAL", title = title.trim().take(120), detail = null, dueAt = dueAt, sourceMessageId = null, threadId = null))
        scheduler.reschedule()
    }
}

@HiltViewModel
class OrderViewModel @Inject constructor(saved: SavedStateHandle, dao: OrderDao) : ViewModel() {
    private val key: String = saved.get<String>("key").orEmpty()
    val order: StateFlow<ShopOrder?> = dao.observe(key).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val events: StateFlow<List<OrderEvent>> = (if (key.isEmpty()) flowOf(emptyList()) else dao.observeEvents(key))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
