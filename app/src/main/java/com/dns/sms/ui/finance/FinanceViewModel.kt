package com.dns.sms.ui.finance

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dns.sms.data.FinanceDao
import com.dns.sms.data.FinanceTxn
import com.dns.sms.parse.AccountGroups
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class FinanceViewModel @Inject constructor(
    dao: FinanceDao,
    val lock: FinanceLock,
) : ViewModel() {
    /** Accounts grouped by bank, with differently-printed endings of one account merged. */
    val banks: StateFlow<List<AccountGroups.Bank>?> = dao.observeAccounts()
        .map { AccountGroups.byBank(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val revealed: StateFlow<Boolean> = lock.revealed
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    saved: SavedStateHandle,
    dao: FinanceDao,
    val lock: FinanceLock,
) : ViewModel() {
    val bank: String = saved.get<String>("bank").orEmpty()
    /** One or more masks (comma separated) of the same account; the first is the one shown. */
    private val masks: List<String> = saved.get<String>("mask").orEmpty().split(',').filter { it.isNotBlank() }
    val mask: String = masks.firstOrNull().orEmpty()
    val account = kotlinx.coroutines.flow.flow { emit(if (masks.isEmpty()) null else dao.latestAccountIn(bank, masks)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val txns: StateFlow<List<FinanceTxn>> = (if (bank.isEmpty() || masks.isEmpty()) flowOf(emptyList()) else dao.observeTxnsIn(bank, masks))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val revealed: StateFlow<Boolean> = lock.revealed
}
