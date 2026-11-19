package com.dns.sms.ui.finance

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import com.dns.sms.MainActivity

/**
 * Marks the window FLAG_SECURE (no screenshots, blank recents preview). Used only by Finance screens;
 * message screens allow screenshots (owner's choice, 2026-11-22). Also guards taps, see [TouchGuard].
 */
@Composable
fun SecureScreen() {
    val activity = LocalActivity.current as? MainActivity
    DisposableEffect(activity) {
        activity?.secureScreen(true)
        onDispose { activity?.secureScreen(false) }
    }
    TouchGuard()
}

/**
 * S11 tapjacking: while shown, taps that arrive through another app's overlay are ignored, so a
 * hidden window can't trick the user into revealing balances, deleting data or changing defaults.
 */
@Composable
fun TouchGuard() {
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        val before = view.filterTouchesWhenObscured
        view.filterTouchesWhenObscured = true
        onDispose { view.filterTouchesWhenObscured = before }
    }
}
