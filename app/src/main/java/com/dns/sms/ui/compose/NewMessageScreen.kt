package com.dns.sms.ui.compose

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dns.sms.R
import com.dns.sms.sms.ContactHit
import com.dns.sms.sms.ContactMatch
import com.dns.sms.security.SensitiveText
import com.dns.sms.ui.Avatar
import com.dns.sms.ui.SensitiveSendDialog
import com.dns.sms.ui.finance.TouchGuard
import com.dns.sms.ui.conversation.MessageList

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewMessageScreen(onBack: () -> Unit, onSent: (Long) -> Unit, vm: NewMessageViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val recipients by vm.recipients.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val body by vm.body.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    val hasContacts by vm.hasContactsPermission.collectAsStateWithLifecycle()
    val scheduledAt by vm.scheduledAt.collectAsStateWithLifecycle()
    var askExactTiming by remember { mutableStateOf(false) }
    if (askExactTiming) ExactTimingDialog(vm.exactTimingIntent()) { askExactTiming = false; onBack() }
    var pendingSensitive by remember { mutableStateOf<Set<SensitiveText.Kind>>(emptySet()) }
    if (pendingSensitive.isNotEmpty()) SensitiveSendDialog(
        pendingSensitive, onSend = { pendingSensitive = emptySet(); vm.send() }, onCancel = { pendingSensitive = emptySet() },
    )
    // Tapjacking: ignore taps arriving through another app's overlay (covers Send).
    TouchGuard()
    val toFocus = remember { FocusRequester() }
    val messageFocus = remember { FocusRequester() }

    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.permissionChanged() }

    // Keyboard up straight away: in "To" when choosing people, in the message once someone is chosen.
    LaunchedEffect(Unit) { runCatching { if (recipients.isEmpty()) toFocus.requestFocus() else messageFocus.requestFocus() } }
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is NewMessageViewModel.Event.Sent -> onSent(e.threadId)
                NewMessageViewModel.Event.SentBulk -> onBack()
                is NewMessageViewModel.Event.Error -> Toast.makeText(ctx, e.message, Toast.LENGTH_SHORT).show()
                NewMessageViewModel.Event.RecipientAdded -> runCatching { messageFocus.requestFocus() }
                is NewMessageViewModel.Event.Scheduled -> {
                    Toast.makeText(ctx, res.getString(R.string.scheduled_done, scheduleLabel(ctx, e.at)), Toast.LENGTH_SHORT).show()
                    if (vm.canSendOnTime()) onBack() else askExactTiming = true
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_message_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        bottomBar = {
            ComposerBar(
                draft = body, onDraft = { vm.body.value = it },
                onSend = {
                    val kinds = SensitiveText.detect(body)
                    if (kinds.isEmpty()) vm.send() else pendingSensitive = kinds
                },
                enabled = !sending && (recipients.isNotEmpty() || ContactMatch.asNumber(query) != null),
                focusRequester = messageFocus,
                scheduledAt = scheduledAt,
                onSchedule = { vm.scheduledAt.value = it },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Recipients: chips + the search field.
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                // "To:", the chosen people and the search field share one line (wrapping when full),
                // so the field sits right after "To:" instead of on a line of its own.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.Center,
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.to_label), Modifier.padding(end = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    recipients.forEach { r ->
                        InputChip(
                            selected = true, onClick = { vm.remove(r) },
                            label = { Text(r.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp)) },
                            avatar = { Avatar(r.label, r.number, size = 24.dp, photo = r.photo) },
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                    // BasicTextField: Material TextField has a 280dp minimum width, which pushed it
                    // onto its own line as soon as one chip was added.
                    val fieldColor = MaterialTheme.colorScheme.onSurface
                    // The String overload is deprecated in Compose 1.13; moving to TextFieldState means
                    // reworking how NewMessageViewModel.query drives contact search, so it's kept for now.
                    @Suppress("DEPRECATION")
                    androidx.compose.foundation.text.BasicTextField(
                        value = query, onValueChange = { vm.query.value = it.take(64) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = fieldColor),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { vm.addTyped() }),
                        // Fills what is left of the line; wraps only when chips leave less than 96dp (room for ~10 digits).
                        modifier = Modifier.weight(1f).widthIn(min = 96.dp).heightIn(min = 48.dp).focusRequester(toFocus),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) Text(
                                    stringResource(R.string.recipient_hint), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                inner()
                            }
                        },
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (recipients.any { isShortCode(it.number) }) Text(
                    stringResource(R.string.short_code_note),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (vm.filledByOtherApp) Text(
                    stringResource(R.string.filled_by_app_note),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (recipients.size > 1) Text(
                    stringResource(R.string.bulk_note),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (query.isNotBlank()) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (!hasContacts) item(key = "allow") {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { contactsPermission.launch(Manifest.permission.READ_CONTACTS) }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Contacts, null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.allow_contacts_search), Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    ContactMatch.asNumber(query)?.let { n ->
                        item(key = "typed") {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { vm.addTyped() }.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
                                Text(stringResource(R.string.send_to_number, n), Modifier.padding(start = 16.dp))
                            }
                        }
                    }
                    items(results, key = { "c-${it.number}-${it.name}" }) { hit -> ContactRow(hit, query) { vm.add(hit) } }
                    if (results.isEmpty() && ContactMatch.asNumber(query) == null) item(key = "none") {
                        Text(stringResource(R.string.no_contact_match), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                if (recents.isNotEmpty()) RecentRow(recents) { vm.add(it) }
                preview?.let { MessageList(it, Modifier.fillMaxSize()) }
            }
        }
    }
}

/** Name with the typed part in blue, number below, phone type (Mobile/Home) on the right. */
@Composable
private fun ContactRow(hit: ContactHit, query: String, onClick: () -> Unit) {
    val name = hit.display
    val range = ContactMatch.highlight(name, query)
    val styled = buildAnnotatedString {
        append(name)
        if (range != null) addStyle(SpanStyle(color = MaterialTheme.colorScheme.primary), range.first, range.last + 1)
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, hit.number, photo = hit.photo)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(styled, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (hit.name != null) Text(hit.number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        hit.typeLabel?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun RecentRow(recents: List<ContactHit>, onPick: (ContactHit) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items(recents, key = { it.number }) { r ->
                Column(
                    Modifier.width(76.dp).clickable { onPick(r) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(r.display, r.number, size = 48.dp, photo = r.photo)
                    Text(r.display, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    if (r.name != null) Text(r.number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 3-6 digit numbers are operator short codes: replies can cost money. */
private fun isShortCode(number: String): Boolean = number.length in 3..6 && number.all { it.isDigit() }
