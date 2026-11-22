package com.dns.sms.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Archive
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import com.dns.sms.settings.SwipeAction
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dns.sms.R
import com.dns.sms.sms.ConversationThread
import com.dns.sms.sms.Message
import com.dns.sms.ui.Avatar
import com.dns.sms.ui.Folder
import com.dns.sms.ui.Format

@Composable
fun ConversationListScreen(
    folder: Folder,
    vm: ConversationListViewModel,
    onOpenThread: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onSwipeFolder: (Int) -> Unit = {},
    /** Room under the list for the floating folder bar. */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
) {
    val changeTabs by vm.changeTabsOnSwipe.collectAsStateWithLifecycle()
    // Settings > Swipe actions > Change tabs on swipe: a horizontal fling moves between folders.
    val swipeTabs = if (changeTabs) Modifier.pointerInput(Unit) {
        var total = 0f
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (kotlin.math.abs(total) > 120f) onSwipeFolder(if (total < 0) 1 else -1) },
        ) { _, dx -> total += dx }
    } else Modifier
    if (folder == Folder.STARRED) {
        StarredList(vm, onOpenThread, modifier.then(swipeTabs), bottomInset)
        return
    }
    val folderState by vm.state.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val unreadOnly by vm.unreadOnly.collectAsStateWithLifecycle()
    val leftSwipe by vm.leftSwipe.collectAsStateWithLifecycle()
    val rightSwipe by vm.rightSwipe.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ConversationThread?>(null) }

    val state = folderState
    if (state == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val all = state.byCategory[folder.category].orEmpty()
    if (all.isEmpty()) {
        EmptyState(
            stringResource(
                when (folder) {
                    Folder.PROMOTIONS -> R.string.empty_promotions
                    Folder.TRANSACTIONS -> R.string.empty_transactions
                    else -> R.string.empty_personal
                },
            ),
            modifier.then(swipeTabs),
        )
        return
    }
    val threads = com.dns.sms.classify.ThreadList.unreadOnly(all, unreadOnly)
    // Perf: time from tapping the folder until its list has been drawn once.
    androidx.compose.runtime.LaunchedEffect(folder) {
        androidx.compose.runtime.withFrameNanos { }
        val start = com.dns.sms.util.Perf.folderSwitchStartedAt
        if (start > 0) com.dns.sms.util.Perf.log("folder ${folder.name} shown (${all.size} threads): ${com.dns.sms.util.Perf.now() - start} ms")
    }
    val selecting = selection.isNotEmpty()

    val perform: (ConversationThread, SwipeAction) -> Unit = { t, action ->
        when (action) {
            SwipeAction.DELETE -> pendingDelete = t
            SwipeAction.MARK_READ -> vm.markRead(listOf(t.id))
            SwipeAction.ARCHIVE -> vm.archive(listOf(t.id), true)
            SwipeAction.NONE -> Unit
        }
    }
    val ctx = LocalContext.current
    LazyColumn(
        modifier.fillMaxSize().then(swipeTabs),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset),
    ) {
        item(key = "header") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.folder_unread_header, all.sumOf { it.unread }, folder.label.lowercase()),
                    Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    stringResource(R.string.unread_only),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Switch(
                    checked = unreadOnly,
                    onCheckedChange = { vm.unreadOnly.value = it },
                    modifier = Modifier.semantics { contentDescription = "Unread only" },
                )
            }
        }
        if (threads.isEmpty()) item(key = "no-unread") { CenteredText(stringResource(R.string.no_unread), Modifier.padding(top = 48.dp)) }
        items(threads, key = { it.id }, contentType = { "thread" }) { t ->
            val row = @Composable { m: Modifier ->
                ThreadRow(
                    t,
                    selected = t.id in selection,
                    pinned = t.id in state.pinned,
                    draft = state.drafts[t.id],
                    muted = t.id in state.muted,
                    onClick = { if (selecting) vm.toggle(t.id) else onOpenThread(t.id) },
                    onLongClick = { vm.toggle(t.id) },
                    // TalkBack can't swipe rows, so the same actions are offered as custom actions.
                    modifier = m.semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(ctx.getString(R.string.mark_read_action)) { vm.markRead(listOf(t.id)); true },
                            CustomAccessibilityAction(ctx.getString(R.string.archive)) { vm.archive(listOf(t.id), true); true },
                            CustomAccessibilityAction(ctx.getString(R.string.delete)) { pendingDelete = t; true },
                        )
                    },
                )
            }
            // Rows slide into place when pinning, archiving or new messages reorder the list (FEAT-3).
            Column(Modifier.animateItem()) {
                // Swipes are off while selecting, or when swipes change tabs instead.
                if (selecting || changeTabs) row(Modifier) else SwipeRow(
                    left = leftSwipe, right = rightSwipe,
                    onAction = { perform(t, it) },
                ) { row(Modifier) }
                HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    pendingDelete?.let { t ->
        ConfirmDialog(
            title = stringResource(R.string.delete_conversation),
            body = stringResource(R.string.delete_conversation_body),
            confirm = stringResource(R.string.delete),
            onConfirm = { pendingDelete = null; vm.delete(listOf(t.id)) },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Swipe actions from Settings (default: right = mark read, left = delete). The row always springs back. */
@Composable
private fun SwipeRow(left: SwipeAction, right: SwipeAction, onAction: (SwipeAction) -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = right != SwipeAction.NONE,
        enableDismissFromEndToStart = left != SwipeAction.NONE,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onAction(right)
                SwipeToDismissBoxValue.EndToStart -> onAction(left)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { state.reset() }
        },
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val action = if (toEnd) right else left
            Row(
                Modifier.fillMaxSize()
                    .background(if (action == SwipeAction.DELETE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End,
            ) {
                Icon(
                    when (action) { SwipeAction.DELETE -> Icons.Filled.Delete; SwipeAction.ARCHIVE -> Icons.Filled.Archive; else -> Icons.Filled.DoneAll },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(swipeLabel(action)), color = MaterialTheme.colorScheme.onPrimary)
            }
        },
    ) { content() }
}

fun swipeLabel(a: SwipeAction): Int = when (a) {
    SwipeAction.DELETE -> R.string.swipe_delete
    SwipeAction.MARK_READ -> R.string.swipe_mark_read
    SwipeAction.ARCHIVE -> R.string.archive
    SwipeAction.NONE -> R.string.none
}

@Composable
private fun StarredList(vm: ConversationListViewModel, onOpenThread: (Long) -> Unit, modifier: Modifier, bottomInset: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val starred by vm.starred.collectAsStateWithLifecycle()
    val starredCount by vm.starredCount.collectAsStateWithLifecycle()
    val list = starred
    when {
        list == null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        list.isEmpty() -> EmptyState(stringResource(R.string.starred_empty), modifier)
        else -> LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset)) {
            items(list, key = { it.key }, contentType = { "starred" }) { m ->
                StarredRow(m, ctx.getString(R.string.me), onClick = { onOpenThread(m.threadId) }, onUnstar = { vm.unstar(m) })
                HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (list.size < starredCount) item(key = "more") {
                TextButton(onClick = vm::loadMoreStarred, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.load_more_starred, list.size, starredCount))
                }
            }
        }
    }
}

@Composable
private fun StarredRow(m: Message, me: String, onClick: () -> Unit, onUnstar: () -> Unit) {
    val ctx = LocalContext.current
    val who = if (m.outgoing) me else m.address.orEmpty()
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick).padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(who, who)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(who, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Format.listDate(ctx, m.dateMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                previewText(m.body).ifEmpty { ctx.getString(R.string.photo) }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onUnstar) { Icon(Icons.Filled.Star, stringResource(R.string.unstar), tint = MaterialTheme.colorScheme.primary) }
    }
}

/** Original illustration + gray helper text. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.ic_empty_tray), contentDescription = null)
        Spacer(Modifier.height(16.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun CenteredText(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body); com.dns.sms.ui.finance.TouchGuard() },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
