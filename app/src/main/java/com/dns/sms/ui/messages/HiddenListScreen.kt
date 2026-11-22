package com.dns.sms.ui.messages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dns.sms.R

enum class HiddenList { ARCHIVED, BLOCKED }

/** Archived conversations (tap to open, Unarchive) or blocked senders (Unblock). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiddenListScreen(
    which: HiddenList,
    onBack: () -> Unit,
    onOpenThread: (Long) -> Unit,
    vm: ConversationListViewModel = hiltViewModel(),
) {
    val folderState by vm.state.collectAsStateWithLifecycle()
    val title = stringResource(if (which == HiddenList.ARCHIVED) R.string.archived else R.string.blocked)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        val state = folderState
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val threads = if (which == HiddenList.ARCHIVED) state.archived else state.blocked
        if (threads.isEmpty()) {
            CenteredText(stringResource(if (which == HiddenList.ARCHIVED) R.string.archived_empty else R.string.blocked_empty), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(threads, key = { it.id }) { t ->
                ThreadRow(
                    t, selected = false, pinned = false,
                    onClick = { onOpenThread(t.id) }, onLongClick = null,
                    trailing = {
                        TextButton(onClick = {
                            if (which == HiddenList.ARCHIVED) vm.archive(listOf(t.id), false) else vm.unblock(t)
                        }) { Text(stringResource(if (which == HiddenList.ARCHIVED) R.string.unarchive else R.string.unblock)) }
                    },
                )
                HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
