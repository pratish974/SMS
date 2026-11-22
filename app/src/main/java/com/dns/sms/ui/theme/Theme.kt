package com.dns.sms.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Dark only, by design (Requirement.md section 2). No light scheme, no dynamic color.
private val SmsColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = OnDark,
    secondary = AccentBlue,
    onSecondary = OnDark,
    background = Background,
    onBackground = OnDark,
    surface = Bars,
    onSurface = OnDark,
    surfaceVariant = Cards,
    onSurfaceVariant = MutedGray,
    surfaceContainer = Bars,
    surfaceContainerHigh = Cards,
    outline = MutedGray,
    outlineVariant = Cards,
)

@Composable
fun SmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SmsColorScheme,
        typography = SmsTypography,
        content = content,
    )
}
