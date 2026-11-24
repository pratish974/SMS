package com.dns.sms.ui.conversation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dns.sms.R
import com.dns.sms.ui.reminders.OpenLinkDialog
import com.dns.sms.util.LinkFinder
import com.dns.sms.security.ScamSignals
import com.dns.sms.security.SensitiveText
import com.dns.sms.security.UrlRisk
import com.dns.sms.ui.ScamWarningRow
import com.dns.sms.ui.SensitiveSendDialog
import com.dns.sms.ui.ShareOtpDialog
import com.dns.sms.ui.SuspiciousAmber
import com.dns.sms.ui.finance.TouchGuard
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import com.dns.sms.classify.OtpDetector
import com.dns.sms.ui.theme.BubbleIn
import com.dns.sms.ui.theme.BubbleOut
import com.dns.sms.util.SensitiveClipboard
import com.dns.sms.classify.Category
import com.dns.sms.ui.MoveToSheet
import com.dns.sms.ui.labelRes
import com.dns.sms.ui.messages.ConfirmDialog
import com.dns.sms.ui.messages.previewText
import com.dns.sms.send.MessageStatus
import com.dns.sms.sms.ConversationThread
import com.dns.sms.sms.Message
import com.dns.sms.sms.PhoneNumbers
import com.dns.sms.ui.Avatar
import com.dns.sms.ui.Format
import com.dns.sms.ui.compose.ComposerBar
import com.dns.sms.ui.compose.ExactTimingDialog
import com.dns.sms.ui.compose.ScheduledItem
import com.dns.sms.ui.compose.pickDateTime
import com.dns.sms.ui.compose.scheduleLabel
import kotlinx.coroutines.flow.receiveAsFlow
import java.time.ZoneId

@Composable
fun ConversationScreen(onBack: () -> Unit, onForward: (String) -> Unit = {}, vm: ConversationViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val thread by vm.thread.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val pageSize by vm.pageSize.collectAsStateWithLifecycle()
    val flags by vm.flags.collectAsStateWithLifecycle()
    val showQuickReplies by vm.showQuickReplies.collectAsStateWithLifecycle()
    val scheduledAt by vm.scheduledAt.collectAsStateWithLifecycle()
    val scheduled by vm.scheduled.collectAsStateWithLifecycle()
    val muted by vm.muted.collectAsStateWithLifecycle()
    val quickReplies by vm.quickReplies.collectAsStateWithLifecycle()
    val simLabel by vm.simLabel.collectAsStateWithLifecycle()
    var pickingSim by remember { mutableStateOf(false) }
    val phonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) { vm.refreshSimLabel(); pickingSim = true } }
    var askExactTiming by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.scheduleNotice.receiveAsFlow().collect { at ->
            Toast.makeText(ctx, res.getString(R.string.scheduled_done, scheduleLabel(ctx, at)), Toast.LENGTH_SHORT).show()
            if (!vm.canSendOnTime()) askExactTiming = true
        }
    }
    if (askExactTiming) ExactTimingDialog(vm.exactTimingIntent()) { askExactTiming = false }
    val messageMeta by vm.messageMeta.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmBlock by rememberSaveable { mutableStateOf(false) }
    var movingConversation by rememberSaveable { mutableStateOf(false) }
    // Message selection (reference screens, Nov 2026): long-press a bubble, tap more to add/remove.
    // Rules live in MessageSelection; this screen only keeps the selected keys.
    var selectedKeys by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val selecting = selectedKeys.isNotEmpty()
    val selectedMessages = messages.orEmpty().filter { it.key in selectedKeys }.sortedBy { it.dateMillis }
    LaunchedEffect(messages) {
        val live = messages?.mapTo(HashSet()) { it.key } ?: return@LaunchedEffect
        selectedKeys = MessageSelection.retain(selectedKeys, live)
    }
    androidx.activity.compose.BackHandler(enabled = selecting) { selectedKeys = emptySet() }
    val toggle: (Message) -> Unit = { m -> selectedKeys = MessageSelection.toggle(selectedKeys, m.key) }
    val clearSelection = { selectedKeys = emptySet() }
    val selectedText = { MessageSelection.joinText(selectedMessages, { it.dateMillis }, { it.body }) }
    var movingMessages by remember { mutableStateOf<List<Message>?>(null) }
    var deletingMessages by remember { mutableStateOf<List<Message>?>(null) }
    var detailsFor by remember { mutableStateOf<List<Message>?>(null) }
    // A one-time code must not leave the app by accident: copy/share/forward asks first (scam victims are talked into this).
    var pendingOtpAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingSensitive by remember { mutableStateOf<Set<SensitiveText.Kind>>(emptySet()) }
    val guardOtp: (() -> Unit) -> Unit = { action ->
        if (selectedMessages.any { !it.outgoing && OtpDetector.extract(it.body) != null }) pendingOtpAction = action else action()
    }
    pendingOtpAction?.let { action -> ShareOtpDialog(onContinue = { pendingOtpAction = null; action() }, onCancel = { pendingOtpAction = null }) }
    if (pendingSensitive.isNotEmpty()) SensitiveSendDialog(
        pendingSensitive, onSend = { pendingSensitive = emptySet(); vm.send() }, onCancel = { pendingSensitive = emptySet() },
    )
    val onSelectionMenu: (MessageSelection.MenuItem) -> Unit = { item ->
        val ms = selectedMessages
        when (item) {
            MessageSelection.MenuItem.SHARE -> guardOtp {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, selectedText())
                clearSelection()
                try { ctx.startActivity(Intent.createChooser(send, res.getString(R.string.share))) } catch (_: ActivityNotFoundException) { }
            }
            MessageSelection.MenuItem.MOVE -> movingMessages = ms
            MessageSelection.MenuItem.ADD_REMINDER -> {
                // Newest selected message is the source; its due date (if the SMS has one) is the starting time.
                val m = ms.last()
                clearSelection()
                pickDateTime(ctx, vm.suggestedReminderAt(m)) { at ->
                    vm.addReminder(m, previewText(m.body).ifEmpty { thread?.title.orEmpty() }, at)
                    Toast.makeText(ctx, R.string.reminder_added, Toast.LENGTH_SHORT).show()
                }
            }
            MessageSelection.MenuItem.FORWARD -> guardOtp { val text = selectedText(); clearSelection(); onForward(text) }
            MessageSelection.MenuItem.DETAILS -> { detailsFor = ms; clearSelection() }
        }
    }
    val toastMoved: (Category?) -> Unit = { c ->
        c?.let { Toast.makeText(ctx, res.getString(R.string.moved_to, res.getString(it.labelRes())), Toast.LENGTH_SHORT).show() }
    }

    LaunchedEffect(Unit) { vm.errors.collect { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }
    // Mark read on open and whenever new messages arrive while the screen is visible.
    LaunchedEffect(messages?.size) { if (messages != null) vm.markRead() }

    // Tapjacking: ignore taps arriving through another app's overlay (covers Send).
    TouchGuard()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (selecting) MessageSelectionBar(
                count = selectedKeys.size,
                allStarred = !MessageSelection.shouldStar(selectedMessages.map { messageMeta[it.key]?.starred == true }),
                onClose = clearSelection,
                onSelectAll = { selectedKeys = MessageSelection.selectAll(selectedKeys, messages.orEmpty().map { it.key }) },
                onDelete = { deletingMessages = selectedMessages },
                onCopy = {
                    guardOtp {
                        ctx.getSystemService(android.content.ClipboardManager::class.java)
                            .setPrimaryClip(android.content.ClipData.newPlainText(res.getString(R.string.message_clip_label), selectedText()))
                        if (android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, R.string.copied, Toast.LENGTH_SHORT).show()
                        clearSelection()
                    }
                },
                onStar = {
                    val star = MessageSelection.shouldStar(selectedMessages.map { messageMeta[it.key]?.starred == true })
                    selectedMessages.forEach { vm.setStarred(it, star) }
                    clearSelection()
                },
                onMenu = onSelectionMenu,
            ) else Header(
                thread, flags, onBack,
                onDelete = { confirmDelete = true },
                onMove = { movingConversation = true },
                onArchive = { archived -> vm.setArchived(archived); if (archived) onBack() },
                onPin = vm::setPinned,
                onBlock = { confirmBlock = true },
                onUnblock = vm::unblock,
                muted = muted,
                onMute = vm::setMuted,
            )
        },
        bottomBar = {
            Column {
            if (scheduled.isNotEmpty()) Column(
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                scheduled.take(3).forEach { m ->
                    ScheduledItem(
                        m, heading = null,
                        onSendNow = { vm.scheduledSendNow(m) },
                        onChangeTime = { pickDateTime(ctx, m.sendAt) { vm.scheduledChangeTime(m, it) } },
                        onEditText = { vm.scheduledEdit(m) },
                        onCancel = { vm.scheduledCancel(m) },
                    )
                }
            }
            ComposerBar(
                draft = draft, onDraft = { vm.draft.value = it },
                onSend = {
                    val kinds = SensitiveText.detect(draft)
                    if (kinds.isEmpty()) vm.send() else pendingSensitive = kinds
                },
                quickReplies = if (showQuickReplies) quickReplies else emptyList(),
                scheduledAt = scheduledAt,
                onSchedule = { vm.scheduledAt.value = it },
                simLabel = simLabel,
                onSim = if (!vm.multiSim) null else {
                    {
                        if (vm.hasPhonePermission()) pickingSim = true
                        else phonePermission.launch(android.Manifest.permission.READ_PHONE_STATE)
                    }
                },
            )
            }
        },
    ) { padding ->
        val list = messages
        if (list == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        MessageList(
            list, Modifier.fillMaxSize().padding(padding),
            starred = { messageMeta[it.key]?.starred == true },
            canLoadEarlier = list.size >= pageSize, onLoadEarlier = vm::loadEarlier,
            onRetry = vm::retry,
            isSelected = { it.key in selectedKeys },
            selecting = selecting,
            onLongClick = toggle,
            onClick = toggle,
        )
    }

    if (movingConversation) {
        val t = thread
        if (t != null) MoveToSheet(
            title = stringResource(R.string.move_conversation_title, t.title),
            note = stringResource(R.string.move_conversation_note),
            current = flags?.category,
            showAutomatic = flags?.overridden == true,
            onPick = { c -> movingConversation = false; vm.moveConversation(c); toastMoved(c) },
            onDismiss = { movingConversation = false },
        )
    }
    movingMessages?.let { ms ->
        // A message's folder is its own override, else the conversation's.
        val current = ms.mapNotNull { messageMeta[it.key]?.category ?: flags?.category }
        MoveMessagesDialog(
            count = ms.size,
            targets = MessageSelection.moveTargets(current),
            onMove = { c, future ->
                movingMessages = null
                clearSelection()
                vm.moveMessages(ms, c, future)
                toastMoved(c)
            },
            onDismiss = { movingMessages = null },
        )
    }
    detailsFor?.let { ms -> MessageDetailsDialog(ms, thread?.title.orEmpty()) { detailsFor = null } }

    if (pickingSim) {
        val sims = remember { vm.simCards() }
        AlertDialog(
            onDismissRequest = { pickingSim = false },
            title = { Text(stringResource(R.string.choose_sim)) },
            text = {
                Column {
                    if (sims.isEmpty()) Text(stringResource(R.string.no_sims))
                    sims.forEach { sim ->
                        ListItem(
                            leadingContent = { Icon(Icons.Filled.SimCard, null) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.clickable { pickingSim = false; vm.chooseSim(sim.subId) },
                        ) { Text(sim.label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingSim = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    deletingMessages?.let { ms ->
        ConfirmDialog(
            title = pluralStringResource(R.plurals.delete_messages_title, ms.size, ms.size),
            body = stringResource(R.string.delete_message_body),
            confirm = stringResource(R.string.delete),
            onConfirm = { deletingMessages = null; selectedKeys = emptySet(); ms.forEach(vm::deleteMessage) },
            onDismiss = { deletingMessages = null },
        )
    }
    if (confirmBlock) {
        ConfirmDialog(
            title = stringResource(R.string.block_title, thread?.title.orEmpty()),
            body = stringResource(R.string.block_body),
            confirm = stringResource(R.string.block),
            onConfirm = { confirmBlock = false; vm.block(); onBack() },
            onDismiss = { confirmBlock = false },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_conversation)) },
            text = { Text(stringResource(R.string.delete_conversation_body)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Header(
    thread: ConversationThread?,
    flags: ConversationViewModel.ThreadFlags?,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
    onArchive: (Boolean) -> Unit,
    onPin: (Boolean) -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    muted: Boolean = false,
    onMute: (Boolean) -> Unit = {},
) {
    val ctx = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val number = thread?.addresses?.singleOrNull()
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        },
        title = {
            if (thread != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(thread.title, thread.addresses.firstOrNull() ?: thread.title, size = 36.dp, photo = thread.photo?.let(Uri::parse))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(thread.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    if (number != null && number != thread.title) {
                        Text(number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        actions = {
            if (number != null && PhoneNumbers.isPhoneLike(number)) {
                // ACTION_DIAL only: no call permissions (Requirement.md section 2).
                IconButton(onClick = {
                    try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) }
                    catch (_: ActivityNotFoundException) { }
                }) { Icon(Icons.Filled.Call, stringResource(R.string.call)) }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete_conversation)) }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_to)) },
                        onClick = { menuOpen = false; onMove() },
                        enabled = thread != null,
                    )
                    val pinned = flags?.pinned == true
                    DropdownMenuItem(
                        text = { Text(stringResource(if (pinned) R.string.unpin_chat else R.string.pin_chat)) },
                        onClick = { menuOpen = false; onPin(!pinned) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (muted) R.string.unmute else R.string.mute)) },
                        onClick = { menuOpen = false; onMute(!muted) },
                    )
                    val archived = flags?.archived == true
                    DropdownMenuItem(
                        text = { Text(stringResource(if (archived) R.string.unarchive else R.string.archive)) },
                        onClick = { menuOpen = false; onArchive(!archived) },
                    )
                    if (thread != null && !thread.isGroup) {
                        val blocked = flags?.blocked == true
                        DropdownMenuItem(
                            text = { Text(stringResource(if (blocked) R.string.unblock else R.string.block)) },
                            onClick = { menuOpen = false; if (blocked) onUnblock() else onBlock() },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun DateSeparator(text: String) {
    Text(
        text, Modifier.fillMaxWidth().padding(vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun Bubble(
    m: Message,
    starred: Boolean,
    onRetry: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean = false,
    selecting: Boolean = false,
    onClick: () -> Unit = {},
) {
    val ctx = LocalContext.current
    val out = m.outgoing
    val otp = remember(m.key, m.body) { if (out) null else OtpDetector.extract(m.body) }
    var openLink by remember { mutableStateOf<String?>(null) }
    // Risk per link (received messages only): colours the link, and a red strip warns about scams.
    val risks = remember(m.key, m.body) {
        if (out) emptyMap() else LinkFinder.find(m.body).associate { r ->
            val url = LinkFinder.toUrl(m.body.substring(r))
            url to UrlRisk.assess(m.address.orEmpty(), m.body, url)
        }
    }
    val dangerousLink = risks.values.any { it.level == UrlRisk.Level.DANGEROUS }
    val otpScam = remember(m.key, m.body) { !out && ScamSignals.otpScam(m.body) }
    // Selected: the bubble turns slate blue and a check sits beside it (reference look). While
    // selecting, a tap anywhere on the row toggles; links and Copy OTP wait until selection ends.
    // The check sits at the free edge of the row: right of received bubbles, left of sent ones.
    Box(
        Modifier.fillMaxWidth()
            .then(if (selecting) Modifier.combinedClickable(onClick = onClick, onLongClick = onClick) else Modifier)
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp),
    ) {
    Column(
        Modifier.fillMaxWidth().padding(start = if (out) 48.dp else 0.dp, end = if (out) 0.dp else 48.dp),
        horizontalAlignment = if (out) Alignment.End else Alignment.Start,
    ) {
        Surface(
            color = if (selected) SelectedBubble else if (out) BubbleOut else BubbleIn,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = { if (selecting) onClick() },
                    onLongClick = onLongClick,
                    onLongClickLabel = stringResource(R.string.select_message),
                ),
        ) {
            // Compact bubble: tight padding, and the time sits at the end of the last line when it fits.
            Column(Modifier.padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (dangerousLink) ScamWarningRow(stringResource(R.string.scam_link_warning))
                if (otpScam) ScamWarningRow(stringResource(R.string.scam_otp_warning))
                m.subject?.takeIf { it.isNotBlank() }?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
                val footer: @Composable () -> Unit = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        if (starred) Icon(Icons.Filled.Star, stringResource(R.string.starred), Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            Format.time(ctx, m.dateMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                if (m.body.isNotEmpty()) TextWithTime(
                    // Links are underlined but only open after the full address is shown (Requirement.md section 9).
                    text = { MessageText(m.body, otp, risks, onLayout = { layout = it }) { if (selecting) onClick() else openLink = it } },
                    footer = footer,
                    layout = { layout },
                ) else Box(Modifier.align(Alignment.End)) { footer() }
                // OTP: divider + centered "Copy OTP" inside the bubble (reference look).
                if (otp != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    TextButton(onClick = { if (selecting) onClick() else SensitiveClipboard.copy(ctx, otp) }, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)) {
                        Text(stringResource(R.string.copy_otp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        openLink?.let { url -> OpenLinkDialog(url, risks[url]) { openLink = null } }
        if (out) DeliveryLine(m, onRetry)
    }
    if (selected) SelectedCheck(Modifier.align(if (out) Alignment.CenterStart else Alignment.CenterEnd))
    }
}

@Composable
private fun SelectedCheck(modifier: Modifier = Modifier) {
    Box(
        modifier.size(28.dp).background(SelectedCheckBg, androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Done, contentDescription = null, Modifier.size(16.dp), tint = androidx.compose.ui.graphics.Color.White)
    }
}

private val SelectedBubble = androidx.compose.ui.graphics.Color(0xFF4A6078)
private val SelectedCheckBg = androidx.compose.ui.graphics.Color(0xFF7D8B99)

/** Under every sent message: "Sending…", "✓ Sent", "✓✓ Delivered: 09 Nov 26 at 19:43", or "Failed" + Retry. */
@Composable
private fun DeliveryLine(m: Message, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
        when (m.status) {
            MessageStatus.SENDING -> Text(stringResource(R.string.status_sending), style = MaterialTheme.typography.labelSmall, color = muted)
            MessageStatus.SENT -> {
                Icon(Icons.Filled.Done, null, Modifier.size(14.dp), tint = muted)
                Text(stringResource(R.string.status_sent), style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(start = 4.dp))
            }
            MessageStatus.DELIVERED -> {
                Icon(Icons.Filled.DoneAll, null, Modifier.size(14.dp), tint = muted)
                Text(
                    stringResource(R.string.status_delivered, Format.deliveredAt(ctx, m.deliveredAt ?: m.dateMillis)),
                    style = MaterialTheme.typography.labelSmall, color = muted, modifier = Modifier.padding(start = 4.dp),
                )
            }
            MessageStatus.FAILED -> {
                Text(stringResource(R.string.status_failed), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
            }
            else -> Unit
        }
    }
}

/** The scrolling message history (newest at the bottom). Shared with the New message preview. */
@Composable
fun MessageList(
    list: List<Message>,
    modifier: Modifier,
    starred: (Message) -> Boolean = { false },
    canLoadEarlier: Boolean = false,
    onLoadEarlier: () -> Unit = {},
    onRetry: (Message) -> Unit = {},
    onLongClick: (Message) -> Unit = {},
    isSelected: (Message) -> Boolean = { false },
    selecting: Boolean = false,
    onClick: (Message) -> Unit = {},
) {
    val items = remember(list) { Timeline.build(list, { it.dateMillis }, ZoneId.systemDefault()).asReversed() }
    LazyColumn(modifier, reverseLayout = true, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(items, key = { item ->
            when (item) {
                is TimelineItem.DateSeparator -> "d-${item.date}"
                is TimelineItem.Entry -> item.value.key
            }
        }) { item ->
            when (item) {
                is TimelineItem.DateSeparator -> DateSeparator(Format.separator(item.date))
                is TimelineItem.Entry -> Bubble(
                    m = item.value,
                    starred = starred(item.value),
                    onRetry = { onRetry(item.value) },
                    onLongClick = { onLongClick(item.value) },
                    selected = isSelected(item.value),
                    selecting = selecting,
                    onClick = { onClick(item.value) },
                )
            }
        }
        if (canLoadEarlier) item(key = "load-earlier") {
            TextButton(onClick = onLoadEarlier, Modifier.fillMaxWidth()) { Text(stringResource(R.string.load_earlier)) }
        }
    }
}

private val OtpGold = androidx.compose.ui.graphics.Color(0xFFE6C15A)

/** Message text with the OTP code underlined in gold and links underlined; tapping a link asks first. */
@Composable
private fun MessageText(body: String, otp: String?, risks: Map<String, UrlRisk.Assessment>, onLayout: (TextLayoutResult) -> Unit = {}, onLink: (String) -> Unit) {
    val linkColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val text = remember(body, otp, risks) {
        buildAnnotatedString {
            append(body)
            otp?.let { code ->
                val i = body.indexOf(code)
                if (i >= 0) addStyle(SpanStyle(color = OtpGold, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.SemiBold), i, i + code.length)
            }
            LinkFinder.find(body).forEach { r ->
                val url = LinkFinder.toUrl(body.substring(r))
                // Dangerous links lose the "link" look (red, no underline) but stay tappable: the dialog explains why.
                val style = when (risks[url]?.level) {
                    UrlRisk.Level.DANGEROUS -> SpanStyle(color = errorColor)
                    UrlRisk.Level.SUSPICIOUS -> SpanStyle(color = SuspiciousAmber, textDecoration = TextDecoration.Underline)
                    else -> SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                }
                addLink(
                    LinkAnnotation.Clickable(url, TextLinkStyles(style)) { onLink(url) },
                    r.first, r.last + 1,
                )
            }
        }
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, onTextLayout = onLayout)
}

/**
 * Message text with its time: the time goes at the end of the last line when there is room
 * (saves a whole line per bubble), otherwise on its own line, right-aligned.
 */
@Composable
private fun TextWithTime(text: @Composable () -> Unit, footer: @Composable () -> Unit, layout: () -> TextLayoutResult?) {
    androidx.compose.ui.layout.Layout(contents = listOf(text, footer)) { (textM, footM), c ->
        val t = textM.first().measure(c.copy(minWidth = 0))
        val f = footM.first().measure(c.copy(minWidth = 0))
        val tl = layout()
        val lastRight = tl?.let { if (it.lineCount > 0) it.getLineRight(it.lineCount - 1).toInt() else t.width } ?: t.width
        val inline = lastRight + f.width <= c.maxWidth
        val w = (if (inline) maxOf(t.width, lastRight + f.width) else maxOf(t.width, f.width)).coerceIn(c.minWidth, c.maxWidth)
        val h = if (inline) maxOf(t.height, f.height) else t.height + f.height
        layout(w, h) {
            t.place(0, 0)
            f.place(w - f.width, h - f.height)
        }
    }
}
