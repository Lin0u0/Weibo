package com.example.myweibo.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Draws a passive indicator at the physical right edge without changing list layout or input. */
@Composable
internal fun Modifier.feedScrollIndicator(
    state: LazyListState,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
    return drawWithContent {
        drawContent()
        val canScrollBackward = state.canScrollBackward
        val canScrollForward = state.canScrollForward
        if (!canScrollBackward && !canScrollForward) return@drawWithContent

        // Read the per-scroll state only in draw, not in composition or layout.
        val layout = state.layoutInfo
        val first = layout.visibleItemsInfo.firstOrNull() ?: return@drawWithContent
        val last = layout.visibleItemsInfo.lastOrNull() ?: return@drawWithContent
        val width = 2.dp.toPx()
        val left = size.width - 2.dp.toPx() - width
        val railTop = (topInset + 4.dp).toPx()
        val railHeight = size.height - railTop - (bottomInset + 4.dp).toPx()
        if (left < 0f) return@drawWithContent
        val thumb = feedScrollThumb(
            rowCount = layout.totalItemsCount,
            visibleStart = feedRowPosition(first.index, first.offset, first.size, layout.viewportStartOffset),
            visibleEnd = feedRowPosition(last.index, last.offset, last.size, layout.viewportEndOffset),
            canScrollBackward = canScrollBackward,
            canScrollForward = canScrollForward,
            reverseLayout = layout.reverseLayout,
            railHeight = railHeight,
            minThumbHeight = 24.dp.toPx(),
        ) ?: return@drawWithContent
        drawRoundRect(
            color = color,
            topLeft = Offset(left, railTop + thumb.top),
            size = Size(width, thumb.height),
            cornerRadius = CornerRadius(width / 2f),
        )
    }
}

internal fun feedRowPosition(index: Int, offset: Int, height: Int, edge: Int): Float =
    index + ((edge - offset).toFloat() / height.coerceAtLeast(1)).coerceIn(0f, 1f)

internal data class FeedScrollThumb(val top: Float, val height: Float)

internal fun feedScrollThumb(
    rowCount: Int,
    visibleStart: Float,
    visibleEnd: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    reverseLayout: Boolean,
    railHeight: Float,
    minThumbHeight: Float,
): FeedScrollThumb? {
    if (rowCount <= 0 || railHeight <= 0f || (!canScrollBackward && !canScrollForward)) return null

    // Offscreen row heights are unknown in a lazy list. Estimate by laid-out row index and
    // fractional visibility, including dividers and footers, rather than by post or server count.
    val total = rowCount.toFloat()
    val start = visibleStart.coerceIn(0f, total)
    val end = visibleEnd.coerceIn(start, total)
    val height = (railHeight * ((end - start) / total)).coerceIn(minThumbHeight.coerceAtMost(railHeight), railHeight)
    val fraction = when {
        !canScrollBackward -> 0f
        !canScrollForward -> 1f
        else -> {
            val hidden = start + (total - end)
            if (hidden > 0f) start / hidden else 0f
        }
    }
    val physicalFraction = if (reverseLayout) 1f - fraction else fraction
    return FeedScrollThumb(top = (railHeight - height) * physicalFraction, height = height)
}
