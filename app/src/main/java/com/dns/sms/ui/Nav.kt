package com.dns.sms.ui

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxSize
import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dns.sms.sms.ContactNames
import com.dns.sms.sms.DefaultSmsGate
import com.dns.sms.settings.AppSettings
import com.dns.sms.settings.LaunchFolder
import com.dns.sms.ui.compose.NewMessageScreen
import com.dns.sms.ui.conversation.ConversationScreen
import com.dns.sms.ui.onboarding.OnboardingScreen
import com.dns.sms.ui.messages.HiddenList
import com.dns.sms.ui.messages.HiddenListScreen
import com.dns.sms.ui.search.SearchScreen
import com.dns.sms.ui.finance.AccountScreen
import com.dns.sms.ui.settings.*
import com.dns.sms.ui.reminders.AddReminderScreen
import com.dns.sms.ui.reminders.OrderDetailScreen
import com.dns.sms.ui.reminders.PastOrdersScreen
import com.dns.sms.ui.reminders.PastRemindersScreen
import com.dns.sms.ui.reminders.RemindersScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dns.sms.ui.finance.FinanceScreen
import com.dns.sms.work.SmsScanWorker
import com.dns.sms.R
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow

sealed interface NavRequest {
    data class Thread(val id: Long) : NavRequest
    data class Compose(val to: List<String>, val body: String?) : NavRequest
    data object Reminders : NavRequest
    data object Scheduled : NavRequest
    data object Finance : NavRequest
}

private const val HOME = "home"
private const val SEARCH = "search"
private const val SCHEDULED = "scheduled"
private const val SETTINGS = "settings"
private const val PAST_REMINDERS = "past_reminders"
private const val PAST_ORDERS = "past_orders"
private const val ADD_REMINDER = "add_reminder"
private const val ARCHIVED = "archived"
private const val BLOCKED = "blocked"

private const val DETAIL_EMPTY = "detail-empty"
private const val TWO_PANE_MIN_DP = 840

fun conversationRoute(threadId: Long) = "conversation/$threadId"

fun orderRoute(key: String) = "order/${Uri.encode(key)}"

fun accountRoute(bank: String, mask: String) = "account/${Uri.encode(bank)}/${Uri.encode(mask)}"

/** [external] marks a compose screen opened by another app (sms: link, share), so the screen can say so. */
fun composeRoute(to: List<String> = emptyList(), body: String? = null, external: Boolean = false) =
    "compose?to=${Uri.encode(to.joinToString(","))}&body=${Uri.encode(body.orEmpty())}&ext=${if (external) 1 else 0}"

@Composable
fun AppNav(gate: DefaultSmsGate, prefs: AppPrefs, contacts: ContactNames, settings: AppSettings, requests: Flow<NavRequest>) {
    val isDefault by gate.isDefault.collectAsStateWithLifecycle()
    val accepted by prefs.disclosureAccepted.collectAsStateWithLifecycle()

    // Being the default SMS app is optional: each fresh launch asks once (disclosure first, then the
    // system prompt). "Not now" continues into the app; the next launch asks again. Rotation doesn't.
    var promptDone by rememberSaveable { mutableStateOf(false) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        gate.refresh()
        promptDone = true
    }
    val askForDefault: () -> Unit = {
        if (prefs.disclosureAccepted.value) roleLauncher.launch(gate.requestIntent()) else promptDone = false
    }
    if (!isDefault && !promptDone && !accepted) {
        OnboardingScreen(
            onAccept = {
                prefs.setDisclosureAccepted(true)
                roleLauncher.launch(gate.requestIntent())
            },
            onNotNow = { promptDone = true },
        )
        return
    }
    if (!isDefault && !promptDone) LaunchedEffect(Unit) { roleLauncher.launch(gate.requestIntent()) }
    if (isDefault) {
        LaunchedEffect(Unit) { prefs.wasDefaultOnce = true }
        RequestRuntimePermissionsOnce(prefs, onResult = contacts::invalidate)
    }

    val nav = rememberNavController()
    val ctx = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    var tabRequest by remember { mutableStateOf<TopTab?>(null) }
    LaunchedEffect(nav) {
        requests.collect { r ->
            when (r) {
                // Not singleTop: the route pattern is the same for every thread, so singleTop kept the open
                // conversation when a notification for ANOTHER thread was tapped. Back returns to the inbox.
                is NavRequest.Thread -> nav.navigate(conversationRoute(r.id)) { popUpTo(HOME) }
                is NavRequest.Compose -> nav.navigate(composeRoute(r.to, r.body, external = true))
                NavRequest.Scheduled -> nav.navigate(SCHEDULED) { launchSingleTop = true }
                NavRequest.Reminders -> {
                    nav.popBackStack(HOME, inclusive = false)
                    tabRequest = TopTab.REMINDERS
                }
                NavRequest.Finance -> {
                    nav.popBackStack(HOME, inclusive = false)
                    tabRequest = TopTab.FINANCE
                }
            }
        }
    }
    NavHost(nav, startDestination = HOME) {
        composable(HOME) {
            // Tablets / unfolded foldables (>= 840 dp wide): list and conversation side by side.
            val windowWidthPx = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width
            val wide = with(androidx.compose.ui.platform.LocalDensity.current) { windowWidthPx.toDp().value } >= TWO_PANE_MIN_DP
            val detailNav = rememberNavController()
            val openThread: (Long) -> Unit = if (wide) {
                { id -> detailNav.navigate(conversationRoute(id)) { popUpTo(DETAIL_EMPTY); launchSingleTop = true } }
            } else {
                { id -> nav.navigate(conversationRoute(id)) }
            }
            val list: @Composable () -> Unit = {
                AppRoot(
                    initialFolder = when (settings.launchFolder.value) {
                        LaunchFolder.PERSONAL -> Folder.PERSONAL
                        LaunchFolder.TRANSACTIONS -> Folder.TRANSACTIONS
                        LaunchFolder.PROMOTIONS -> Folder.PROMOTIONS
                        LaunchFolder.LAST_RECEIVED -> null
                    },
                    onOpenThread = openThread,
                    onCompose = { nav.navigate(composeRoute()) },
                    onSearch = { nav.navigate(SEARCH) },
                    onArchived = { nav.navigate(ARCHIVED) },
                    onScheduled = { nav.navigate(SCHEDULED) },
                    onBlocked = { nav.navigate(BLOCKED) },
                    onSettings = { nav.navigate(SETTINGS) },
                    isDefault = isDefault,
                    onSetDefault = askForDefault,
                    tabRequest = tabRequest,
                    onTabRequestHandled = { tabRequest = null },
                    reminders = TabSlot(
                        content = { m ->
                            RemindersScreen(
                                onPastReminders = { nav.navigate(PAST_REMINDERS) },
                                onPastOrders = { nav.navigate(PAST_ORDERS) },
                                onOpenOrder = { nav.navigate(orderRoute(it)) },
                                onOpenThread = { nav.navigate(conversationRoute(it)) },
                                modifier = m,
                            )
                        },
                        menu = listOf(
                            MenuEntry(res.getString(R.string.refresh_cards)) { SmsScanWorker.enqueue(ctx, full = true) },
                            MenuEntry(res.getString(R.string.add_reminder)) { nav.navigate(ADD_REMINDER) },
                            MenuEntry(res.getString(R.string.show_past_reminders)) { nav.navigate(PAST_REMINDERS) },
                            MenuEntry(res.getString(R.string.show_past_orders)) { nav.navigate(PAST_ORDERS) },
                        ),
                        onFab = { nav.navigate(ADD_REMINDER) },
                    ),
                    finance = TabSlot(
                        content = { m -> FinanceScreen(onOpenAccount = { a -> nav.navigate(accountRoute(a.bank, a.masks.joinToString(","))) }, modifier = m) },
                        menu = listOf(MenuEntry(res.getString(R.string.refresh_cards)) { SmsScanWorker.enqueue(ctx, full = true) }),
                    ),
                )
            }
            if (!wide) list() else androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(0.42f)) { list() }
                androidx.compose.material3.VerticalDivider()
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(0.58f)) {
                    NavHost(detailNav, startDestination = DETAIL_EMPTY) {
                        composable(DETAIL_EMPTY) { com.dns.sms.ui.messages.EmptyState(stringResource(R.string.select_conversation)) }
                        composable(
                            "conversation/{threadId}",
                            arguments = listOf(navArgument("threadId") { type = NavType.LongType }),
                        ) {
                            ConversationScreen(
                                onBack = { detailNav.popBackStack(DETAIL_EMPTY, inclusive = false) },
                                onForward = { nav.navigate(composeRoute(body = it)) },
                            )
                        }
                    }
                }
            }
        }
        composable(
            "account/{bank}/{mask}",
            arguments = listOf(navArgument("bank") { type = NavType.StringType }, navArgument("mask") { type = NavType.StringType }),
        ) {
            AccountScreen(onBack = { nav.popBackStack() })
        }
        composable(PAST_REMINDERS) { PastRemindersScreen(onBack = { nav.popBackStack() }) }
        composable(PAST_ORDERS) { PastOrdersScreen(onBack = { nav.popBackStack() }, onOpenOrder = { nav.navigate(orderRoute(it)) }) }
        composable(ADD_REMINDER) { AddReminderScreen(onBack = { nav.popBackStack() }) }
        composable("order/{key}", arguments = listOf(navArgument("key") { type = NavType.StringType })) {
            OrderDetailScreen(onBack = { nav.popBackStack() })
        }
        composable(SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, open = { nav.navigate("$SETTINGS/$it") }) }
        composable("$SETTINGS/general") { GeneralSettingsScreen(onBack = { nav.popBackStack() }) }
        composable("$SETTINGS/rules") { MessageRulesScreen(onBack = { nav.popBackStack() }) }
        composable("$SETTINGS/notifications") { NotificationSettingsScreen(onBack = { nav.popBackStack() }) }
        composable("$SETTINGS/swipe") { SwipeSettingsScreen(onBack = { nav.popBackStack() }) }
        composable("$SETTINGS/about") { AboutScreen(onBack = { nav.popBackStack() }) }
        composable("$SETTINGS/quick_replies") { QuickRepliesScreen(onBack = { nav.popBackStack() }) }
        composable("$SETTINGS/status") { AppStatusScreen(onBack = { nav.popBackStack() }) }
        composable("$SETTINGS/backup") { BackupScreen(onBack = { nav.popBackStack() }) }
        composable(SCHEDULED) { com.dns.sms.ui.compose.ScheduledScreen(onBack = { nav.popBackStack() }) }
        composable(SEARCH) {
            SearchScreen(onBack = { nav.popBackStack() }, onOpenThread = { nav.navigate(conversationRoute(it)) })
        }
        composable(ARCHIVED) {
            HiddenListScreen(HiddenList.ARCHIVED, onBack = { nav.popBackStack() }, onOpenThread = { nav.navigate(conversationRoute(it)) })
        }
        composable(BLOCKED) {
            HiddenListScreen(HiddenList.BLOCKED, onBack = { nav.popBackStack() }, onOpenThread = { nav.navigate(conversationRoute(it)) })
        }
        composable(
            "conversation/{threadId}",
            arguments = listOf(navArgument("threadId") { type = NavType.LongType }),
        ) {
            ConversationScreen(onBack = { nav.popBackStack() }, onForward = { nav.navigate(composeRoute(body = it)) })
        }
        composable(
            "compose?to={to}&body={body}&ext={ext}",
            arguments = listOf("to", "body", "ext").map { name ->
                navArgument(name) { type = NavType.StringType; defaultValue = "" }
            },
        ) {
            NewMessageScreen(
                onBack = { nav.popBackStack() },
                onSent = { threadId ->
                    nav.navigate(conversationRoute(threadId)) { popUpTo(HOME) }
                },
            )
        }
    }
}

/** Asks for notifications (and optional contacts) once, right after we become the default SMS app. */
@Composable
private fun RequestRuntimePermissionsOnce(prefs: AppPrefs, onResult: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    LaunchedEffect(Unit) {
        if (!prefs.askedRuntimePermissions) {
            prefs.askedRuntimePermissions = true
            val perms = buildList {
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_CONTACTS)
            }
            launcher.launch(perms.toTypedArray())
        }
    }
}
