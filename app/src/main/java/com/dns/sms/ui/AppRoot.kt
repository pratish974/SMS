package com.dns.sms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dns.sms.R
import com.dns.sms.classify.Category
import com.dns.sms.ui.messages.ConversationListScreen
import com.dns.sms.ui.messages.ConversationListViewModel
import com.dns.sms.ui.messages.SelectionBar
import com.dns.sms.ui.theme.Cards

enum class TopTab(val label: String, val icon: ImageVector, val badgeTint: Color) {
    // Header card (reference design 2026-11-22): each tab has its own accent for the icon tile.
    MESSAGES("Messages", Icons.Outlined.Forum, Color(0xFF8AA4F8)),
    REMINDERS("Reminders", Icons.Outlined.NotificationsActive, Color(0xFFB39DFA)),
    FINANCE("Finance", Icons.Outlined.AccountBalanceWallet, Color(0xFF7DDBA8)),
}

/** Bottom folders. Starred is a flag, not a [Category], so it has none. */
enum class Folder(val label: String, val icon: ImageVector, val category: Category?) {
    PERSONAL("Personal", Icons.Outlined.Person, Category.PERSONAL),
    TRANSACTIONS("Transactions", Icons.AutoMirrored.Outlined.CompareArrows, Category.TRANSACTIONS),
    PROMOTIONS("Promotions", Icons.Outlined.Campaign, Category.PROMOTIONS),
    STARRED("Starred", Icons.Outlined.StarOutline, null),
}

/** An overflow-menu entry for a tab. */
data class MenuEntry(val label: String, val onClick: () -> Unit)

/** What a non-Messages tab contributes to the shell. */
class TabSlot(
    val content: @Composable (Modifier) -> Unit,
    val menu: List<MenuEntry> = emptyList(),
    val onFab: (() -> Unit)? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(
    /** null = the folder with the most recent message (Settings > General > LastReceived). */
    initialFolder: Folder?,
    onOpenThread: (Long) -> Unit,
    onCompose: () -> Unit,
    onSearch: () -> Unit,
    onArchived: () -> Unit,
    onScheduled: () -> Unit,
    onBlocked: () -> Unit,
    onSettings: () -> Unit,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    reminders: TabSlot,
    finance: TabSlot,
    tabRequest: TopTab? = null,
    onTabRequestHandled: () -> Unit = {},
    onTabChanged: (TopTab) -> Unit = {},
    onFolderChanged: (Folder) -> Unit = {},
    listVm: ConversationListViewModel = hiltViewModel(),
) {
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var folderIndex by rememberSaveable { mutableIntStateOf((initialFolder ?: Folder.PERSONAL).ordinal) }
    var folderChosen by rememberSaveable { mutableStateOf(initialFolder != null) }
    var menuOpen by remember { mutableStateOf(false) }
    val tab = TopTab.entries[tabIndex]
    val onMessages = tab == TopTab.MESSAGES
    val folder = Folder.entries[folderIndex]
    val selection by listVm.selection.collectAsStateWithLifecycle()
    val folderState by listVm.state.collectAsStateWithLifecycle()
    val selecting = onMessages && selection.isNotEmpty()
    LaunchedEffect(folderState != null) {
        val st = folderState ?: return@LaunchedEffect
        if (folderChosen) return@LaunchedEffect
        folderChosen = true
        val newest = Folder.entries.filter { it.category != null }
            .maxByOrNull { f -> st.byCategory[f.category].orEmpty().maxOfOrNull { it.dateMillis } ?: Long.MIN_VALUE }
        newest?.let { folderIndex = it.ordinal }
    }
    LaunchedEffect(tabRequest) {
        tabRequest?.let { tabIndex = it.ordinal; onTabRequestHandled() }
    }
    val barState = rememberFolderBarState()
    LaunchedEffect(tabIndex, folderIndex) {
        barState.expand() // big bar whenever you arrive in a section
        com.dns.sms.util.Perf.folderSwitchStartedAt = com.dns.sms.util.Perf.now()
        listVm.clearSelection()
        onTabChanged(tab)
        if (onMessages) onFolderChanged(folder)
    }

    val menu: List<MenuEntry> = when (tab) {
        TopTab.MESSAGES -> listOf(
            MenuEntry(stringResource(R.string.mark_all_read)) { listVm.markAllRead(folder) },
            MenuEntry(stringResource(R.string.scheduled), onScheduled),
            MenuEntry(stringResource(R.string.archived), onArchived),
            MenuEntry(stringResource(R.string.blocked), onBlocked),
            MenuEntry(stringResource(R.string.settings), onSettings),
        )
        TopTab.REMINDERS -> reminders.menu + MenuEntry(stringResource(R.string.settings), onSettings)
        TopTab.FINANCE -> finance.menu + MenuEntry(stringResource(R.string.settings), onSettings)
    }
    val fab: (() -> Unit)? = when (tab) {
        // Without the SMS role nothing can be sent, so the pencil asks to become the default instead.
        TopTab.MESSAGES -> if (selecting) null else if (isDefault) onCompose else onSetDefault
        TopTab.REMINDERS -> reminders.onFab
        TopTab.FINANCE -> finance.onFab
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                if (selecting) SelectionBar(listVm, folder)
                HeaderCard(title = if (selecting) null else stringResource(R.string.app_name), selected = tabIndex, onSelect = { tabIndex = it }) {
                        if (onMessages) IconButton(onClick = onSearch) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                menu.forEach { e ->
                                    DropdownMenuItem(text = { Text(e.label) }, onClick = { menuOpen = false; e.onClick() })
                                }
                            }
                        }
                }
                if (!isDefault) DefaultSmsBanner(onSetDefault)
            }
        },
        floatingActionButton = {
            // Messages draws its own FAB above the floating folder bar.
            if (!onMessages) fab?.let { onClick ->
                FloatingActionButton(
                    onClick = onClick,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    // Messages has its own floating compose button; this one is Reminders' "Add".
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add))
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tab) {
            TopTab.MESSAGES -> Box(m.fillMaxSize().nestedScroll(barState.scrollConnection)) {
                ConversationListScreen(
                    folder, listVm, onOpenThread, Modifier.fillMaxSize(),
                    onSwipeFolder = { delta -> folderIndex = (folderIndex + delta).coerceIn(0, Folder.entries.lastIndex) },
                    bottomInset = if (selecting) 0.dp else FolderBarState.ContentInset,
                )
                if (!selecting) {
                    val transactionsUnread = folderState?.byCategory?.get(Category.TRANSACTIONS)?.any { it.unread > 0 } == true
                    FloatingFolderBar(
                        selected = folderIndex,
                        collapsed = barState.collapsed,
                        dotOn = if (transactionsUnread) Folder.TRANSACTIONS else null,
                        onSelect = { folderIndex = it },
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 4.dp),
                    )
                    val fabLift by animateDpAsState(if (barState.collapsed) 68.dp else 84.dp, label = "fabLift")
                    FloatingActionButton(
                        onClick = fab ?: onCompose,
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = fabLift),
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.new_message_title))
                    }
                }
            }
            TopTab.REMINDERS -> reminders.content(m)
            TopTab.FINANCE -> finance.content(m)
        }
    }
}

/** Icon-over-label tabs; the selected icon sits in a round light badge, with an underline. */
private val HeaderCardColor = Color(0xFF13161E)
private val HeaderMuted = Color(0xFF9AA0AC)

/**
 * Top header (reference design 2026-11-22): a rounded card with the current tab's name and actions,
 * then the three tabs as tinted icon tiles. The selected tab gets a brighter tile, white label and
 * an accent underline. [title] is null while conversations are selected (the selection bar shows instead).
 */
@Composable
private fun HeaderCard(
    title: String?,
    selected: Int,
    onSelect: (Int) -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    // Full-width bar right under the status bar; title size and even tab spacing follow the
    // reference screen, the coloured icon tiles are ours.
    Column(
        Modifier.fillMaxWidth().background(HeaderCardColor)
            .then(if (title != null) Modifier.statusBarsPadding() else Modifier),
    ) {
        if (title != null) Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title, Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color.White,
            )
            actions()
        }
        Row(Modifier.fillMaxWidth().padding(top = if (title != null) 0.dp else 8.dp).selectableGroup()) {
            TopTab.entries.forEachIndexed { i, t -> HeaderTab(t, i == selected, Modifier.weight(1f)) { onSelect(i) } }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun HeaderTab(t: TopTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(36.dp).background(
                t.badgeTint.copy(alpha = if (selected) 0.30f else 0.12f),
                androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(t.icon, contentDescription = null, tint = t.badgeTint, modifier = Modifier.size(20.dp))
        }
        Text(
            t.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else HeaderMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
        )
        Box(
            Modifier.size(width = 48.dp, height = 3.dp)
                .background(if (selected) t.badgeTint else Color.Transparent, CircleShape),
        )
    }
}

/** Shown while the app isn't the default SMS app: it can't read or send SMS until it is (Play policy). */
@Composable
private fun DefaultSmsBanner(onSetDefault: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Cards).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.not_default_banner), Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
        )
        androidx.compose.material3.TextButton(onClick = onSetDefault) { Text(stringResource(R.string.set_default)) }
    }
}
