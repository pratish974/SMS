package com.dns.sms.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dns.sms.send.SmsCounter
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dns.sms.R

/**
 * Bottom message bar shared by New message and conversations (Requirement.md section 5, Composer).
 * Text only: pictures and voice notes are a planned milestone. One line: [schedule] [message] [send];
 * Send appears while typing and shows a clock when a time is set.
 * The text field never moves in the tree: rebuilding it closed the keyboard (BUG-1).
 */
@Composable
fun ComposerBar(
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean = true,
    quickReplies: List<String> = emptyList(),
    focusRequester: FocusRequester = remember { FocusRequester() },
    /** FEAT-1: chosen send time, or null to send now. */
    scheduledAt: Long? = null,
    /** FEAT-1: called with a picked time, or null to clear it. Null hides the schedule button. */
    onSchedule: ((Long?) -> Unit)? = null,
    /** Dual SIM: label of the SIM used ("SIM 2"), or null to show just a SIM icon. */
    simLabel: String? = null,
    /** Dual SIM: tap to pick a SIM. Null hides the button (single-SIM phones). */
    onSim: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    val canSend = enabled && draft.isNotBlank()
    val expanded = draft.isNotEmpty() || focused

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        Column {
            if (quickReplies.isNotEmpty() && draft.isEmpty()) QuickReplyTiles(quickReplies, onDraft)
            if (scheduledAt != null && onSchedule != null) ScheduledChip(scheduledAt, onChange = { pickDateTime(ctx, scheduledAt) { onSchedule(it) } }) { onSchedule(null) }
            Surface(color = MaterialTheme.colorScheme.surface) {
                // One line: [schedule] [message field] [send]. The field keeps its place in the tree
                // (BUG-1); Send is added after it, so nothing before the field ever changes.
                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (onSchedule != null) ScheduleButton(scheduledAt != null) { pickDateTime(ctx, scheduledAt) { onSchedule(it) } }
                    if (onSim != null) SimButton(simLabel, onSim)
                    MessageField(draft, onDraft, focusRequester, Modifier.weight(1f)) { focused = it }
                    if (expanded) {
                        // "12 / 2" = characters left in this SMS / number of SMS; only near the limit.
                        val count = remember(draft) { SmsCounter.count(draft) }
                        if (SmsCounter.worthShowing(count)) {
                            val a11y = androidx.compose.ui.res.pluralStringResource(R.plurals.sms_count_a11y, count.parts, count.remaining, count.parts)
                            Text(
                            "${count.remaining} / ${count.parts}",
                            Modifier.padding(start = 6.dp).semantics { contentDescription = a11y },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (count.parts > 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        }
                        SendButton(canSend, scheduled = scheduledAt != null, onSend)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageField(draft: String, onDraft: (String) -> Unit, focus: FocusRequester, modifier: Modifier, onFocus: (Boolean) -> Unit) {
    TextField(
        value = draft, onValueChange = onDraft,
        placeholder = { Text(stringResource(R.string.type_message)) },
        modifier = modifier.focusRequester(focus).onFocusChanged { onFocus(it.isFocused) },
        maxLines = 6,
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

@Composable
private fun SimButton(label: String?, onClick: () -> Unit) {
    Box(
        Modifier.padding(end = 4.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.SimCard, stringResource(R.string.choose_sim), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            if (label != null) Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScheduleButton(active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Filled.Schedule, stringResource(R.string.schedule_message),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ScheduledChip(at: Long, onChange: () -> Unit, onClear: () -> Unit) {
    val ctx = LocalContext.current
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onChange)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.scheduled_for, scheduleLabel(ctx, at)), Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = onClear) { Icon(Icons.Filled.Close, stringResource(R.string.clear_schedule), Modifier.size(18.dp)) }
    }
}

/** Round Send button; shows a clock when a time is set, so it's clear Send will schedule. */
@Composable
private fun SendButton(enabled: Boolean, scheduled: Boolean, onClick: () -> Unit) {
    val bg = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        scheduled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        Modifier.padding(start = 6.dp, end = 4.dp).size(44.dp).clip(CircleShape).background(bg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (scheduled) Icons.Filled.Schedule else Icons.AutoMirrored.Filled.Send,
            stringResource(if (scheduled) R.string.schedule_button else R.string.send),
            tint = if (scheduled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.background,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Tile-style quick replies; they fill the text box, never send by themselves. */
@Composable
private fun QuickReplyTiles(replies: List<String>, onPick: (String) -> Unit) {
    LazyRow(
        Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
    ) {
        items(replies) { r ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.width(96.dp).heightIn(min = 48.dp).clickable { onPick(r) },
            ) {
                Text(r, Modifier.padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
