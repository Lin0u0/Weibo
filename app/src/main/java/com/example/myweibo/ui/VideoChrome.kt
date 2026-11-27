package com.example.myweibo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.myweibo.data.bottomBarBlurSamplingPadding
import com.example.myweibo.data.bottomBarGlassBlurDp
import com.example.myweibo.data.normalizeBottomBarGlassAmount
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/** The existing bottom-bar sliders also drive app-owned video chrome. */
@Immutable
internal data class VideoChromeStyle(val frostAmount: Float, val glassAmount: Float) {
    val blurDp = bottomBarGlassBlurDp(frostAmount)
    val refraction = normalizeBottomBarGlassAmount(glassAmount)
    val hasEffects = blurDp > 0f || refraction > 0f

    // Video/cover pixels can be white in either app theme. Keep white labels legible
    // even without a sampled image or when both sliders are zero.
    fun surfaceAlpha(effectsApplied: Boolean): Float =
        if (effectsApplied) 0.68f + 0.12f * normalizeBottomBarGlassAmount(frostAmount) else 0.72f
}

internal val LocalVideoChromeStyle = compositionLocalOf { VideoChromeStyle(0f, 1f) }

internal enum class VideoChromeLens(val heightDp: Float, val amountDp: Float) {
    Compact(10f, 18f),
    Standard(12f, 20f),
    Boost(16f, 28f),
}

@Composable
internal fun rememberVideoChromeBackdrop(enabled: Boolean = true): LayerBackdrop? =
    if (enabled && LocalVideoChromeStyle.current.hasEffects) rememberLayerBackdrop() else null

/** Attach only to Compose content; never wrap a PlayerView or WebView in this source. */
internal fun Modifier.videoChromeBackdropSource(backdrop: LayerBackdrop?, drawSource: Boolean = true): Modifier =
    if (backdrop == null) this else layerBackdrop(backdrop, drawSource = drawSource)

private val ChromeShape = RoundedCornerShape(percent = 50)

@Composable
private fun Modifier.videoChromeSurface(backdrop: Backdrop?, profile: VideoChromeLens): Modifier {
    val style = LocalVideoChromeStyle.current
    val surface = Color.Black.copy(alpha = style.surfaceAlpha(backdrop != null && style.hasEffects))
    val border = Color.White.copy(alpha = 0.24f)
    if (backdrop == null || !style.hasEffects) {
        return clip(ChromeShape).background(surface).border(0.5.dp, border, ChromeShape)
    }
    return drawBackdrop(
        backdrop = backdrop,
        shape = { ChromeShape },
        effects = {
            val radius = style.blurDp.dp.toPx()
            val lensHeight = (profile.heightDp * style.refraction).dp.toPx()
            if (radius > 0f) {
                blur(radius, TileMode.Clamp)
                padding = bottomBarBlurSamplingPadding(radius, lensHeight)
            }
            if (style.refraction > 0f) {
                lens(lensHeight, (profile.amountDp * style.refraction).dp.toPx())
            }
        },
        highlight = { Highlight.Default.copy(alpha = if (style.refraction > 0f) 0.5f else 0.2f) },
        shadow = if (profile != VideoChromeLens.Boost) ({ Shadow.Default }) else null,
        onDrawSurface = { drawRect(surface) },
    ).border(0.5.dp, border, ChromeShape)
}

@Composable
internal fun VideoChromeCapsule(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    profile: VideoChromeLens = VideoChromeLens.Standard,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.videoChromeSurface(backdrop, profile), content = content)
}

@Composable
internal fun VideoChromeButton(
    onClick: () -> Unit,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    profile: VideoChromeLens = VideoChromeLens.Standard,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.videoChromeSurface(backdrop, profile)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
