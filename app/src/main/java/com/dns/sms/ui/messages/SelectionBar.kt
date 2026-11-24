package com.dns.sms.ui.messages

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LibraryAddCheck
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dns.sms.R
import com.dns.sms.ui.Folder
import com.dns.sms.ui.MoveToSheet
import com.dns.sms.ui.labelRes

/** Top bar shown while conversations are selected: count, pin, select all, move, delete, overflow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionBar(vm: ConversationListViewModel, folder: Folder) {
    val ctx = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val selection by vm.selection.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }

    BackHandler { vm.clearSelection() }
    val allPinned = selection.all { it in state?.pinned.orEmpty() }
    val allMuted = selection.all { it in state?.muted.orEmpty() }

    val selectedDesc = pluralStringResource(R.plurals.selected_count, selection.size, selection.size)
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        navigationIcon = { IconButton(onClick = vm::clearSelection) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.close_selection)) } },
        title = { Text(selection.size.toString(), Modifier.semantics { contentDescription = selectedDesc }) },
        actions = {
            IconButton(onClick = vm::togglePinSelected) {
                Icon(if (allPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, stringResource(if (allPinned) R.string.unpin else R.string.pin))
            }
            IconButton(onClick = { vm.selectAll(state?.byCategory?.get(folder.category).orEmpty().map { it.id }) }) {
                Icon(Icons.Outlined.LibraryAddCheck, stringResource(R.string.select_all))
            }
            IconButton(onClick = { moving = true }) { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, stringResource(R.string.move_to)) }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    // Order and wording follow the reference design (Requirement screens, Nov 2026).
                    DropdownMenuItem(text = { Text(stringResource(if (allMuted) R.string.unmute_sender else R.string.mute_sender)) }, onClick = {
                        menu = false; vm.toggleMuteSelected()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.block_sender)) }, onClick = {
                        menu = false; confirmBlock = true
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.archive)) }, onClick = {
                        menu = false; vm.act { vm.archive(it, true) }
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.mark_read_action)) }, onClick = {
                        menu = false; vm.act { vm.markRead(it) }
                    })
                }
            }
        },
    )

    if (moving) {
        val threads = vm.selectedThreads()
        val cats = threads.mapNotNull { state?.threadCategory?.get(it.id) }.toSet()
        MoveToSheet(
            title = stringResource(R.string.move_to),
            note = stringResource(R.string.move_conversation_note),
            current = cats.singleOrNull(),
            showAutomatic = threads.any { it.id in state?.overridden.orEmpty() },
            onPick = { c ->
                moving = false
                vm.act { vm.move(threads, c) }
                c?.let { Toast.makeText(ctx, res.getString(R.string.moved_to, res.getString(it.labelRes())), Toast.LENGTH_SHORT).show() }
            },
            onDismiss = { moving = false },
        )
    }
    if (confirmDelete) ConfirmDialog(
        title = pluralStringResource(R.plurals.delete_conversations_title, selection.size, selection.size),
        body = stringResource(R.string.delete_conversation_body),
        confirm = stringResource(R.string.delete),
        onConfirm = { confirmDelete = false; vm.act { vm.delete(it) } },
        onDismiss = { confirmDelete = false },
    )
    if (confirmBlock) {
        val threads = vm.selectedThreads().filter { !it.isGroup }
        ConfirmDialog(
            title = threads.singleOrNull()?.let { stringResource(R.string.block_title, it.title) }
                ?: pluralStringResource(R.plurals.block_many_title, threads.size, threads.size),
            body = stringResource(R.string.block_body),
            confirm = stringResource(R.string.block),
            onConfirm = { confirmBlock = false; vm.act { vm.block(threads) } },
            onDismiss = { confirmBlock = false },
        )
    }
}
