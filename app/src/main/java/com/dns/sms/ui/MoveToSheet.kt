package com.dns.sms.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dns.sms.R
import com.dns.sms.classify.Category

@StringRes
fun Category.labelRes(): Int = when (this) {
    Category.PERSONAL -> R.string.folder_personal
    Category.TRANSACTIONS -> R.string.folder_transactions
    Category.PROMOTIONS -> R.string.folder_promotions
}

private fun Category.icon(): ImageVector = when (this) {
    Category.PERSONAL -> Icons.Filled.Person
    Category.TRANSACTIONS -> Icons.AutoMirrored.Filled.ReceiptLong
    Category.PROMOTIONS -> Icons.Filled.LocalOffer
}

/**
 * Folder picker for "Move To". [current] gets a check mark (and is announced as selected).
 * "Automatic" appears only when the user's choice is in effect, to undo it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToSheet(
    title: String,
    note: String,
    current: Category?,
    showAutomatic: Boolean,
    onPick: (Category?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp).selectableGroup()) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Category.entries.forEach { c ->
                Option(stringResource(c.labelRes()), null, c.icon(), selected = c == current) { onPick(c) }
            }
            if (showAutomatic) {
                Option(stringResource(R.string.automatic), stringResource(R.string.automatic_desc), Icons.Filled.AutoAwesome, selected = false) { onPick(null) }
            }
        }
    }
}

@Composable
private fun Option(label: String, supporting: String?, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = if (selected) ({ Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }) else null,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}
