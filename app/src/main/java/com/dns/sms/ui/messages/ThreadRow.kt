package com.dns.sms.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dns.sms.R
import com.dns.sms.sms.ConversationThread
import com.dns.sms.ui.Avatar
import com.dns.sms.ui.Format

/** One conversation row. In selection mode a check mark replaces the avatar. */
@Composable
fun ThreadRow(
    t: ConversationThread,
    selected: Boolean,
    pinned: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    draft: String? = null,
    muted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val bold = t.unread > 0
    Row(
        modifier.fillMaxWidth().heightIn(min = 72.dp)
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            // Reference look: neutral gray disc with a plain check, not the accent color.
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            Avatar(t.title, t.addresses.firstOrNull() ?: t.title, photo = t.photo?.let(android.net.Uri::parse))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.title, Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (muted) Icon(
                    Icons.Filled.NotificationsOff, stringResource(R.string.muted),
                    Modifier.size(16.dp).padding(end = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (pinned) Icon(
                    Icons.Filled.PushPin, stringResource(R.string.pinned),
                    Modifier.size(16.dp).padding(end = 4.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    remember(t.dateMillis) { Format.listDate(ctx, t.dateMillis) },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (bold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draft != null) {
                    val label = stringResource(R.string.draft_label)
                    Text(
                        remember(draft, label) {
                            androidx.compose.ui.text.buildAnnotatedString {
                                pushStyle(androidx.compose.ui.text.SpanStyle(color = DraftRed, fontWeight = FontWeight.SemiBold))
                                append(label); append(" ")
                                pop()
                                append(previewText(draft))
                            }
                        },
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                } else Text(
                    remember(t.snippet) { previewText(t.snippet) }, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (bold) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (bold) UnreadBadge(t.unread)
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun UnreadBadge(count: Int) {
    Box(
        Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private val DraftRed = androidx.compose.ui.graphics.Color(0xFFE57373)

private val whitespace = Regex("""\s+""")

/**
 * One-line preview: promotional SMS often start with blank lines or long runs of spaces, which made
 * the single visible line look almost empty. Collapse all whitespace so every folder shows full text.
 */
fun previewText(text: String): String = whitespace.replace(text, " ").trim()
