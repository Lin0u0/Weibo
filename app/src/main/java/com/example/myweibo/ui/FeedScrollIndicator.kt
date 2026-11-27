package com.example.myweibo.ui

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myweibo.R

/** A passive right-edge thumb with a count of loaded posts above the readable viewport. */
@Composable
internal fun Modifier.feedScrollIndicator(
    state: LazyListState,
    rows: FeedScrollRows,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
    bottomOcclusionInRoot: (() -> Float)? = null,
    enabled: Boolean = true,
): Modifier {
    if (!enabled) return this
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val background = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val resources = LocalResources.current
    val density = LocalDensity.current
    val topPx = with(density) { topInset.toPx() }
    val bottomPx = with(density) { bottomInset.toPx() }
    val railMargin = with(density) { 4.dp.toPx() }
    val railTop = topPx + railMargin
    var listTopInRoot by remember { mutableFloatStateOf(Float.NaN) }
    fun railBottom(height: Float): Float = feedScrollRailBottom(
        height = height,
        bottomInset = bottomPx,
        listTopInRoot = listTopInRoot,
        dockTopInRoot = bottomOcclusionInRoot?.invoke() ?: Float.POSITIVE_INFINITY,
    ) - railMargin
    // Scroll updates are consumed in draw/semantics, not by recomposing the list. The spoken
    // value changes only with the count, and isn't a live region or a separate focus stop.
    val postsAbove by remember(state, rows, topPx, bottomPx, railMargin, bottomOcclusionInRoot) {
        derivedStateOf(structuralEqualityPolicy()) {
            val layout = state.layoutInfo
            if ((!state.canScrollBackward && !state.canScrollForward) || layout.visibleItemsInfo.isEmpty() ||
                railBottom(layout.viewportSize.height.toFloat()) <= railTop
            ) null else feedPostsAboveViewport(
                rows,
                layout.visibleItemsInfo,
                viewportEdge = if (layout.reverseLayout) layout.viewportEndOffset - topPx
                    else layout.viewportStartOffset + topPx,
                reverseLayout = layout.reverseLayout,
            )
        }
    }
    val trackPosition = if (bottomOcclusionInRoot == null) Modifier else Modifier.onGloballyPositioned {
        listTopInRoot = it.positionInRoot().y
    }
    return then(trackPosition).semantics {
        postsAbove?.let { stateDescription = resources.getString(R.string.feed_scroll_posts_above, it) }
    }.drawWithContent {
        drawContent()
        val count = postsAbove ?: return@drawWithContent
        val layout = state.layoutInfo
        val first = layout.visibleItemsInfo.firstOrNull() ?: return@drawWithContent
        val last = layout.visibleItemsInfo.lastOrNull() ?: return@drawWithContent
        val width = 2.dp.toPx()
        val left = size.width - 4.dp.toPx() - width
        val railHeight = railBottom(size.height) - railTop
        if (left < 0f) return@drawWithContent
        val thumb = feedScrollThumb(
            rowCount = layout.totalItemsCount,
            visibleStart = feedRowPosition(first.index, first.offset, first.size, layout.viewportStartOffset),
            visibleEnd = feedRowPosition(last.index, last.offset, last.size, layout.viewportEndOffset),
            canScrollBackward = state.canScrollBackward,
            canScrollForward = state.canScrollForward,
            reverseLayout = layout.reverseLayout,
            railHeight = railHeight,
            thumbHeight = 24.dp.toPx(),
        ) ?: return@drawWithContent
        val thumbTop = railTop + thumb.top
        drawRoundRect(
            color = color,
            topLeft = Offset(left, thumbTop),
            size = Size(width, thumb.height),
            cornerRadius = CornerRadius(width / 2f),
        )
        val label = measurer.measure(count.toString(), style = labelStyle, maxLines = 1)
        val horizontalPadding = 4.dp.toPx()
        val labelWidth = label.size.width + 2 * horizontalPadding
        val labelHeight = label.size.height + 2.dp.toPx()
        val labelLeft = left - 6.dp.toPx() - labelWidth
        val labelTop = feedScrollLabelTop(railTop, railHeight, thumb, labelHeight)
        if (labelLeft >= 0f && labelTop != null) {
            drawRoundRect(
                color = background,
                topLeft = Offset(labelLeft, labelTop),
                size = Size(labelWidth, labelHeight),
                cornerRadius = CornerRadius(labelHeight / 2f),
            )
            drawText(label, color = textColor, topLeft = Offset(labelLeft + horizontalPadding, labelTop + 1.dp.toPx()))
        }
    }
}

/** Posts start at index zero, optionally interrupted by one divider; all other rows trail them. */
internal data class FeedScrollRows(
    val postCount: Int,
    val dividerIndex: Int? = null,
    val collapsedPostCount: Int = 0,
) {
    private val rowEnd = postCount + if (dividerIndex != null) 1 else 0

    private fun postsBefore(index: Int): Int = index.coerceIn(0, rowEnd) -
        if (dividerIndex != null && dividerIndex < index) 1 else 0

    fun postsAbove(boundary: Int, reverseLayout: Boolean): Int {
        val visiblePostRowsAbove = if (reverseLayout) postCount - postsBefore(boundary) else postsBefore(boundary)
        val collapsedAbove = dividerIndex != null &&
            (if (reverseLayout) dividerIndex >= boundary else dividerIndex < boundary)
        return visiblePostRowsAbove + if (collapsedAbove) collapsedPostCount else 0
    }
}

/** [laidOutRows] are in lazy index order, with offsets in the lazy list's padding coordinates. */
internal fun feedPostsAboveViewport(
    rows: FeedScrollRows,
    laidOutRows: List<LazyListItemInfo>,
    viewportEdge: Float,
    reverseLayout: Boolean = false,
): Int? {
    if (laidOutRows.isEmpty()) return null
    // With no sticky items, every index beyond the measured range on the top side is
    // already wholly above the viewport. For measured rows, a single visible pixel is enough.
    val boundary = if (reverseLayout) {
        laidOutRows.lastOrNull { it.offset < viewportEdge }?.let { it.index + 1 } ?: laidOutRows.first().index
    } else {
        laidOutRows.firstOrNull { it.offset + it.size > viewportEdge }?.index ?: (laidOutRows.last().index + 1)
    }
    return rows.postsAbove(boundary, reverseLayout)
}

internal fun feedScrollRailBottom(height: Float, bottomInset: Float, listTopInRoot: Float, dockTopInRoot: Float): Float {
    val systemBottom = height - bottomInset
    if (!listTopInRoot.isFinite() || !dockTopInRoot.isFinite()) return systemBottom
    return minOf(systemBottom, dockTopInRoot - listTopInRoot)
}

internal fun feedScrollLabelTop(railTop: Float, railHeight: Float, thumb: FeedScrollThumb, labelHeight: Float): Float? {
    if (labelHeight > railHeight) return null
    return railTop + (thumb.top + (thumb.height - labelHeight) / 2f)
        .coerceIn(0f, railHeight - labelHeight)
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
    thumbHeight: Float,
): FeedScrollThumb? {
    if (rowCount <= 0 || railHeight <= 0f || (!canScrollBackward && !canScrollForward)) return null

    // Offscreen row heights are unknown in a lazy list. Estimate by laid-out row index and
    // fractional visibility, including dividers and footers, rather than by post or server count.
    val total = rowCount.toFloat()
    val start = visibleStart.coerceIn(0f, total)
    val end = visibleEnd.coerceIn(start, total)
    val height = thumbHeight.coerceAtMost(railHeight)
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
