package com.dns.sms

import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

import kotlinx.coroutines.launch

import androidx.lifecycle.lifecycleScope

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import android.view.WindowManager
import com.dns.sms.ui.finance.FinanceLock
import com.dns.sms.work.SmsScanWorker
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dns.sms.intent.ComposeIntentParser
import com.dns.sms.sms.ContactNames
import com.dns.sms.sms.DefaultSmsGate
import com.dns.sms.ui.AppNav
import com.dns.sms.ui.AppPrefs
import com.dns.sms.ui.NavRequest
import com.dns.sms.ui.theme.SmsTheme
import com.dns.sms.ui.Format
import com.dns.sms.settings.AppSettings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var gate: DefaultSmsGate
    @Inject lateinit var prefs: AppPrefs
    @Inject lateinit var contacts: ContactNames
    @Inject lateinit var financeLock: FinanceLock
    @Inject lateinit var settings: AppSettings
    @Inject lateinit var folders: dagger.Lazy<com.dns.sms.classify.Folders>

    private val navRequests = Channel<NavRequest>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the splash until the inbox is ready (max 1 s) instead of flashing a spinner first.
        // (Injected fields like `gate` exist only after super.onCreate.)
        var ready = false
        installSplashScreen().setKeepOnScreenCondition { !ready }
        super.onCreate(savedInstanceState)
        if (!gate.isDefaultNow()) ready = true else lifecycleScope.launch {
            kotlinx.coroutines.withTimeoutOrNull(1.seconds) { folders.get().observe().first() }
            ready = true
        }
        enableEdgeToEdge()
        // Recents thumbnail never shows message text; screenshots stay allowed (Finance uses FLAG_SECURE).
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        setContent {
            val fontScale by settings.fontScale.flow.collectAsState()
            val use24h by settings.use24h.flow.collectAsState()
            val follow by settings.use24hFollowSystem.flow.collectAsState()
            Format.force24h = if (follow) null else use24h
            // Settings > Font size multiplies the system font size; it never replaces it.
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, base.fontScale * fontScale.factor),
            ) {
                SmsTheme {
                    // Screens read Format.force24h when they compose, so a changed setting shows up on the
                    // next screen. (Keying AppNav on it would rebuild navigation and drop the back stack.)
                    AppNav(gate, prefs, contacts, settings, navRequests.receiveAsFlow())
                }
            }
        }
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        // Stop all SMS access immediately if another app became the default (Requirement.md section 8).
        gate.refresh()
        // WorkManager setup touches its own database: keep it off the main thread (startup perf).
        if (gate.isDefaultNow()) {
            val app = applicationContext
            Thread({ SmsScanWorker.enqueueIfIdle(app) }, "scan-enqueue").start()
        }
        // Contacts permission may have been changed in system settings while we were away.
        contacts.refreshPermission()
    }

    override fun onStop() {
        super.onStop()
        // Balances are re-masked whenever the app leaves the screen (Requirement.md section 4, Finance).
        financeLock.hide()
    }

    private var secureScreens = 0

    /** FLAG_SECURE while any Finance screen is on screen: no screenshots or recents thumbnail. */
    fun secureScreen(add: Boolean) {
        secureScreens = (secureScreens + if (add) 1 else -1).coerceAtLeast(0)
        if (secureScreens > 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    /**
     * Intents from other apps are untrusted: everything goes through ComposeIntentParser. This activity
     * is exported, so it no longer understands any internal action (open thread, reminders, scheduled).
     */
    private fun handle(intent: Intent?) {
        intent ?: return
        val request = when (intent.action) {
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> ComposeIntentParser.parse(intent.dataString)?.let { d ->
                NavRequest.Compose(d.recipients, d.body ?: extraText(intent))
            }
            Intent.ACTION_SEND -> {
                val d = ComposeIntentParser.parse(intent.dataString)
                NavRequest.Compose(d?.recipients.orEmpty(), d?.body ?: extraText(intent))
            }
            else -> null
        }
        request?.let { navRequests.trySend(it) }
        // Internal destinations only ever arrive through InternalNav (set by our non-exported trampoline).
        InternalNav.take()?.let { navRequests.trySend(it) }
    }

    private fun extraText(intent: Intent): String? = ComposeIntentParser.cleanBody(
        intent.getStringExtra("sms_body") ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
    )

}
