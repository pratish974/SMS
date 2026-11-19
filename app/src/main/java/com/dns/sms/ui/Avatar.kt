package com.dns.sms.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dns.sms.sms.PhoneNumbers

private val avatarColors = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF6C00), Color(0xFF8E24AA),
    Color(0xFF43A047), Color(0xFFD81B60), Color(0xFF1E88E5), Color(0xFF6D4C41),
)

/** Decorative: the name is always shown next to it, so no content description. */
@Composable
fun Avatar(name: String, key: String, modifier: Modifier = Modifier, size: Dp = 44.dp, photo: android.net.Uri? = null) {
    if (photo != null) {
        val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
        val bitmap by androidx.compose.runtime.produceState(AvatarCache.get(photo), photo) {
            if (value == null) value = AvatarCache.load(resolver, photo)
        }
        bitmap?.let {
            androidx.compose.foundation.Image(
                it, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = modifier.size(size).clip(CircleShape),
            )
            return
        }
        // No photo yet (loading) or it can't be read: initials below.
    }
    val color = remember(key) { avatarColors[PhoneNumbers.colorIndex(key, avatarColors.size)] }
    val initials = remember(name) { PhoneNumbers.initials(name) }
    Box(
        modifier.size(size).background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Contact photos, decoded small (160 px) once and kept in memory, so scrolling the list doesn't
 * decode them again. ~100 KB each; 150 photos max (~15 MB).
 */
private object AvatarCache {
    private val cache = android.util.LruCache<android.net.Uri, androidx.compose.ui.graphics.ImageBitmap>(150)

    fun get(uri: android.net.Uri): androidx.compose.ui.graphics.ImageBitmap? = cache.get(uri)

    suspend fun load(resolver: android.content.ContentResolver, uri: android.net.Uri): androidx.compose.ui.graphics.ImageBitmap? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                android.graphics.ImageDecoder.decodeBitmap(
                    android.graphics.ImageDecoder.createSource(resolver, uri),
                ) { d: android.graphics.ImageDecoder, info: android.graphics.ImageDecoder.ImageInfo, _: android.graphics.ImageDecoder.Source ->
                    val scale = minOf(1f, 160f / maxOf(info.size.width, info.size.height, 1))
                    d.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }.asImageBitmap()
            }.getOrNull()?.also { cache.put(uri, it) }
        }
}
