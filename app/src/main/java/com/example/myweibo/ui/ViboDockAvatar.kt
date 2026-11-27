package com.example.myweibo.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myweibo.data.FeedImage
import com.example.myweibo.data.ImageSaveHelper
import com.example.myweibo.data.StoredWeiboAccount
import com.example.myweibo.data.UserProfile
import com.example.myweibo.data.ViboDockEntry
import com.example.myweibo.data.normalizeBottomBarGlassAmount
import com.example.myweibo.ui.liquidglass.drawBottomBarGlassBackdrop
import com.example.myweibo.ui.theme.isAppLightTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.isRenderEffectSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

internal data class ViboDockAvatarRequest(val accountId: String, val url: String)

internal fun resolveViboDockAvatarRequest(
    verifiedAccountId: String?,
    activeAccountId: String?,
    signedIn: Boolean,
    profile: UserProfile?,
    storedAccounts: List<StoredWeiboAccount>,
): ViboDockAvatarRequest? {
    val ownerId = verifiedAccountId?.takeIf { signedIn && it == activeAccountId } ?: return null
    val url = profile?.takeIf { it.id == ownerId }?.avatarUrl?.takeIf(String::isNotBlank)
        ?: storedAccounts.firstOrNull { it.id == ownerId }?.avatarUrl
    return url?.trim()?.takeIf {
        it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
    }?.let { ViboDockAvatarRequest(ownerId, it) }
}

internal fun interface ViboDockAvatarLoader {
    suspend fun load(request: ViboDockAvatarRequest): ImageBitmap?
    fun cached(request: ViboDockAvatarRequest): ImageBitmap? = null
}

private object CachedDockAvatarLoader : ViboDockAvatarLoader {
    private val cache = LruCache<ViboDockAvatarRequest, ImageBitmap>(12)

    override fun cached(request: ViboDockAvatarRequest): ImageBitmap? = cache.get(request)

    override suspend fun load(request: ViboDockAvatarRequest): ImageBitmap? {
        val url = request.url
        val decoded = withContext(Dispatchers.IO) {
            ImageSaveHelper.loadBytes(FeedImage(id = url, thumbnailUrl = url, largeUrl = url))
                ?.decodeDockAvatar()
        }
        if (decoded != null) cache.put(request, decoded)
        return decoded
    }
}

@Composable
internal fun ViboDockAvatar(
    request: ViboDockAvatarRequest?,
    contentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop,
    frostAmount: Float,
    glassAmount: Float,
    modifier: Modifier = Modifier,
    loader: ViboDockAvatarLoader = CachedDockAvatarLoader,
) {
    // Reset at composition, not inside the effect: even a reused URL may now have a different owner.
    var avatar by remember(request, loader) { mutableStateOf(request?.let(loader::cached)) }
    LaunchedEffect(request, loader) {
        if (request != null && avatar == null) avatar = loader.load(request)
    }

    ViboDockAvatarEndcap(avatar, contentColor, selected, onClick, backdrop, frostAmount, glassAmount, modifier)
}

@Composable
private fun ViboDockAvatarEndcap(
    avatar: ImageBitmap?,
    contentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop,
    frostAmount: Float,
    glassAmount: Float,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val isLightTheme = isAppLightTheme()
    val selectionTint = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    var motionEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        val motionScale = coroutineContext[MotionDurationScale]
        snapshotFlow { motionScale?.scaleFactor != 0f }.collect { motionEnabled = it }
    }
    val pressScale = if (motionEnabled) {
        animateFloatAsState(
            targetValue = if (pressed) 0.96f else 1f,
            animationSpec = tween(durationMillis = if (pressed) 90 else 140),
            label = "dock-avatar-press",
        )
    } else {
        null
    }

    Box(
        modifier = modifier.size(56.dp)
            .semantics { contentDescription = MainTab.Mine.label }
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null, // The ripple belongs to the visual circle, not the hit box.
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.matchParentSize()
                .graphicsLayer {
                    val scale = pressScale?.value ?: 1f
                    scaleX = scale
                    scaleY = scale
                }
                .drawViboDockEndcapSurface(backdrop, frostAmount, glassAmount, isLightTheme)
                .clip(CircleShape)
                .then(if (selected) Modifier.background(selectionTint) else Modifier)
                .indication(interactionSource, ripple(color = contentColor))
                .testTag("vibo_dock_avatar_glass"),
            contentAlignment = Alignment.Center,
        ) {
            if (avatar != null) {
                Image(
                    bitmap = avatar,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp).clip(CircleShape).testTag("vibo_dock_avatar_image"),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    painter = painterResource(ViboDockEntry.Profile.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).testTag("vibo_dock_avatar_fallback"),
                    tint = contentColor,
                )
            }
            if (focused) {
                Box(Modifier.matchParentSize().padding(2.dp).border(2.dp, contentColor, CircleShape))
            }
        }
    }
}

/** One background for either Vibo endcap, independent of the icon or photo it hosts. */
internal fun Modifier.drawViboDockEndcapSurface(
    backdrop: Backdrop,
    frostAmount: Float,
    glassAmount: Float,
    isLightTheme: Boolean,
): Modifier = if (
    isRenderEffectSupported() && backdrop !== emptyBackdrop() &&
    (normalizeBottomBarGlassAmount(frostAmount) > 0f || normalizeBottomBarGlassAmount(glassAmount) > 0f)
) {
    drawBottomBarGlassBackdrop(backdrop, frostAmount, glassAmount, isLightTheme)
} else {
    background(if (isLightTheme) Color.White else Color(0xFF121212), CircleShape)
}

private const val DockAvatarDecodeSize = 192

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

@Preview(showBackground = true)
@Composable
private fun ViboDockAvatarEndcapPreview() {
    val avatar = remember {
        Bitmap.createBitmap(88, 88, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xFFF3B782.toInt())
            val canvas = Canvas(this)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF70405A.toInt() }
            canvas.drawCircle(44f, 30f, 15f, paint)
            canvas.drawOval(15f, 47f, 73f, 95f, paint)
        }.asImageBitmap()
    }
    MaterialTheme {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ViboDockAvatarEndcap(avatar, Color.Black, true, {}, emptyBackdrop(), 0f, 0f)
            ViboDockAvatarEndcap(null, Color.Black, false, {}, emptyBackdrop(), 0f, 0f)
        }
    }
}
