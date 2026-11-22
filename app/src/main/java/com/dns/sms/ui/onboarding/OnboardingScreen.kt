package com.dns.sms.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dns.sms.R

/**
 * Prominent disclosure (accept/decline) before any SMS access, then the default-SMS role request.
 * Also shown, as "paused", if another app takes the SMS role later.
 */
/**
 * Prominent disclosure (Requirement.md section 8) shown before the first default-SMS request.
 * "Not now" is allowed: the app opens without SMS access and asks again on the next launch.
 */
@Composable
fun OnboardingScreen(onAccept: () -> Unit, onNotNow: () -> Unit) {
    com.dns.sms.ui.finance.TouchGuard()
    val appName = stringResource(R.string.app_name)
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Title(stringResource(R.string.disclosure_title))
            Body(stringResource(R.string.disclosure_body, appName))
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAccept, Modifier.fillMaxWidth()) { Text(stringResource(R.string.accept)) }
            OutlinedButton(onClick = onNotNow, Modifier.fillMaxWidth()) { Text(stringResource(R.string.not_now)) }
        }
    }
}

@Composable
private fun Title(text: String) = Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
