package com.dns.sms.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dns.sms.R
import com.dns.sms.sms.Message
import com.dns.sms.ui.Avatar
import com.dns.sms.ui.Format
import com.dns.sms.ui.messages.CenteredText
import com.dns.sms.ui.messages.ThreadRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenThread: (Long) -> Unit, vm: SearchViewModel = hiltViewModel()) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                title = {
                    TextField(
                        value = query, onValueChange = { vm.query.value = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                },
                actions = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) {
                        Icon(Icons.Filled.Close, stringResource(R.string.clear_search))
                    }
                },
            )
        },
    ) { padding ->
        if (query.trim().length == 1) {
            CenteredText(stringResource(R.string.search_min_chars), Modifier.padding(padding))
            return@Scaffold
        }
        val r = results ?: return@Scaffold
        if (r.threads.isEmpty() && r.messages.isEmpty()) {
            CenteredText(stringResource(R.string.search_no_results), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if (r.threads.isNotEmpty()) {
                item(key = "h-threads") { SectionHeader(stringResource(R.string.search_conversations)) }
                items(r.threads, key = { "t-${it.id}" }) { t ->
                    ThreadRow(t, selected = false, pinned = false, onClick = { onOpenThread(t.id) }, onLongClick = null)
                }
            }
            if (r.messages.isNotEmpty()) {
                item(key = "h-messages") { SectionHeader(stringResource(R.string.search_messages)) }
                items(r.messages, key = { "m-${it.key}" }) { m ->
                    MessageResult(m, vm.titleFor(m.threadId, r.threads), r.query, onClick = { onOpenThread(m.threadId) })
                    HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun MessageResult(m: Message, threadTitle: String?, query: String, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val who = if (m.outgoing) stringResource(R.string.me) else (threadTitle ?: m.address.orEmpty())
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(who, m.address ?: who)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(who, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Format.listDate(ctx, m.dateMillis), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                highlight(excerpt(m.body, query), query, MaterialTheme.colorScheme.primary),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Starts the preview a little before the first match so long messages still show it. */
private fun excerpt(body: String, query: String): String {
    val i = body.indexOf(query, ignoreCase = true)
    return if (i <= 40) body else "…" + body.substring(i - 30)
}

private fun highlight(text: String, query: String, color: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    var from = 0
    while (true) {
        val i = text.indexOf(query, from, ignoreCase = true)
        if (i < 0) break
        addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), i, i + query.length)
        from = i + query.length
    }
}
