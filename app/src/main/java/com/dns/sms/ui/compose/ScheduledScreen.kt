package com.dns.sms.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dns.sms.R
import com.dns.sms.data.ScheduledDao
import com.dns.sms.data.ScheduledMessage
import com.dns.sms.scheduled.ScheduledMessages
import com.dns.sms.sms.ContactNames
import com.dns.sms.ui.messages.CenteredText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ScheduledViewModel @Inject constructor(
    dao: ScheduledDao,
    private val scheduled: ScheduledMessages,
    private val contacts: ContactNames,
) : ViewModel() {
    val items: StateFlow<List<ScheduledMessage>?> = dao.observeOpen().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun title(m: ScheduledMessage) = m.recipientList.joinToString(", ") { contacts.displayName(it) }
    fun sendNow(m: ScheduledMessage) { viewModelScope.launch { scheduled.sendNow(m.id) } }
    fun changeTime(m: ScheduledMessage, at: Long) { viewModelScope.launch { scheduled.changeTime(m.id, at) } }
    fun cancel(m: ScheduledMessage) { viewModelScope.launch { scheduled.cancel(m.id) } }
}

/** All pending (and failed) scheduled messages across conversations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduledScreen(onBack: () -> Unit, vm: ScheduledViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scheduled)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        val list = items.orEmpty()
        if (items != null && list.isEmpty()) {
            CenteredText(stringResource(R.string.scheduled_empty), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { m ->
                ScheduledItem(
                    m, heading = vm.title(m),
                    onSendNow = { vm.sendNow(m) },
                    onChangeTime = { pickDateTime(ctx, m.sendAt) { vm.changeTime(m, it) } },
                    onEditText = null,
                    onCancel = { vm.cancel(m) },
                )
            }
        }
    }
}
