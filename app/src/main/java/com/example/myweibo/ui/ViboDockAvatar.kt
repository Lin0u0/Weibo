package com.example.myweibo.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.myweibo.data.FeedImage
import com.example.myweibo.data.ImageSaveHelper
import com.example.myweibo.data.ViboDockEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val dockAvatarCache = LruCache<String, ImageBitmap>(12)
private const val DockAvatarDecodeSize = 192

@Composable
internal fun ViboDockAvatar(avatarUrl: String?, contentColor: Color, selected: Boolean) {
    val url = avatarUrl?.trim()?.takeIf {
        it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
    }
    // Reset even before the new request starts: changing accounts must never retain an old avatar.
    var avatar by remember(url) { mutableStateOf(url?.let(dockAvatarCache::get)) }
    LaunchedEffect(url) {
        if (url == null || avatar != null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) {
            val bytes = ImageSaveHelper.loadBytes(FeedImage(id = url, thumbnailUrl = url, largeUrl = url))
            bytes?.decodeDockAvatar()
        }
        if (decoded != null) {
            dockAvatarCache.put(url, decoded)
            avatar = decoded
        }
    }

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(contentColor.copy(alpha = 0.08f))
            .then(if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val current = avatar
        if (current == null) {
            Icon(
                painter = painterResource(ViboDockEntry.Profile.iconRes),
                contentDescription = null,
                modifier = Modifier.size(22.dp).testTag("vibo_dock_avatar_fallback"),
                tint = contentColor,
            )
        } else {
            Image(
                bitmap = current,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun ByteArray.decodeDockAvatar(): ImageBitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, options)
    if (options.outWidth <= 0 || options.outHeight <= 0) return null
    var sample = 1
    val largest = maxOf(options.outWidth, options.outHeight)
    while (largest / (sample * 2) >= DockAvatarDecodeSize) sample *= 2
    options.inJustDecodeBounds = false
    options.inSampleSize = sample
    return BitmapFactory.decodeByteArray(this, 0, size, options)?.asImageBitmap()
}
