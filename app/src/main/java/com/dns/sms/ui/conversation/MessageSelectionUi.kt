package com.dns.sms.ui.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LibraryAddCheck
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dns.sms.R
import com.dns.sms.classify.Category
import com.dns.sms.sms.Message
import com.dns.sms.ui.Format
import com.dns.sms.ui.labelRes
import com.dns.sms.ui.conversation.MessageSelection.MenuItem

/** Top bar while messages are selected: ✕, count, Select all, Delete, Copy, Star, overflow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageSelectionBar(
    count: Int,
    allStarred: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onStar: () -> Unit,
    onMenu: (MenuItem) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val selectedDesc = pluralStringResource(R.plurals.selected_count, count, count)
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringResource(R.string.close_selection)) } },
        title = { Text(count.toString(), Modifier.semantics { contentDescription = selectedDesc }) },
        actions = {
            BarAction(stringResource(R.string.select_all), Icons.Outlined.LibraryAddCheck, onSelectAll)
            BarAction(stringResource(R.string.delete), Icons.Outlined.Delete, onDelete)
            BarAction(stringResource(R.string.copy_text), Icons.Outlined.ContentCopy, onCopy)
            BarAction(
                stringResource(if (allStarred) R.string.unstar else R.string.star),
                if (allStarred) Icons.Filled.Star else Icons.Outlined.StarOutline, onStar,
            )
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    MessageSelection.menu(count).forEach { item ->
                        DropdownMenuItem(text = { Text(stringResource(item.labelRes())) }, onClick = { menu = false; onMenu(item) })
                    }
                }
            }
        },
    )
}

private fun MenuItem.labelRes(): Int = when (this) {
    MenuItem.SHARE -> R.string.share_sms
    MenuItem.MOVE -> R.string.move_to
    MenuItem.ADD_REMINDER -> R.string.add_reminder
    MenuItem.FORWARD -> R.string.forward_sms
    MenuItem.DETAILS -> R.string.view_message_details
}

/** Icon button with a long-press label (reference shows "Select All" on hold). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(androidx.compose.material3.TooltipAnchorPosition.Below),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick) { Icon(icon, contentDescription = label) }
    }
}

/**
 * "Move N messages to": the other folders as radio buttons, plus "Move future SMS" which also
 * moves the sender so new messages land there. Move stays disabled until a folder is picked.
 */
@Composable
fun MoveMessagesDialog(
    count: Int,
    targets: List<Category>,
    onMove: (Category, future: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf<Category?>(null) }
    var future by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(pluralStringResource(R.plurals.move_messages_title, count, count))
        },
        text = {
            Column(Modifier.selectableGroup()) {
                targets.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = picked == c, role = Role.RadioButton, onClick = { picked = c }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(c.labelRes()), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        RadioButton(selected = picked == c, onClick = null)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(value = future, role = Role.Checkbox, onValueChange = { future = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = future, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.move_future_sms))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { picked?.let { onMove(it, future) } }, enabled = picked != null) { Text(stringResource(R.string.move)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** "Message details": From/Received (or To/Sent) for each selected message, oldest first. */
@Composable
fun MessageDetailsDialog(messages: List<Message>, threadTitle: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.message_details)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                messages.sortedBy { it.dateMillis }.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val who = m.address?.takeIf { it.isNotBlank() } ?: threadTitle
                    DetailLine(stringResource(if (m.outgoing) R.string.detail_to else R.string.detail_from), who)
                    DetailLine(
                        stringResource(if (m.outgoing) R.string.detail_sent else R.string.detail_received),
                        Format.deliveredAt(ctx, m.dateMillis),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row {
        Text(label, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(value)
    }
}
