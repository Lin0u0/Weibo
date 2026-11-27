package com.example.myweibo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myweibo.R
import com.example.myweibo.data.isTimelineRowMeaningfullyVisible
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor

private const val UnreadExposureMillis = 450L

@Composable
internal fun ObserveTimelineUnread(
    home: HomeTimelineReadingState,
    state: LazyListState,
    exposed: Boolean,
    topInsetPx: Float,
    dockTopInRoot: () -> Float,
) {
    val scope = home.unreadScope ?: return
    if (!exposed || !home.feedInitialScrollReconciled || home.visibleUnreadCount == 0) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var lifecycleState by remember(lifecycle) { mutableStateOf(lifecycle.currentState) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> lifecycleState = lifecycle.currentState }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val systemBottomPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)
    val readInsets by rememberUpdatedState(topInsetPx to systemBottomPx)
    val dockTop by rememberUpdatedState(dockTopInRoot)
    var listBounds by remember { mutableStateOf<Rect?>(null) }

    // This transparent sibling does not affect lazy list padding, row keys or scroll offset.
    Box(Modifier.fillMaxSize().onGloballyPositioned { listBounds = it.boundsInRoot() })
    LaunchedEffect(home, scope, state, lifecycle) {
        fun exposedStatusIds(): Set<String> {
            if (lifecycleState != Lifecycle.State.RESUMED || home.unreadScope != scope) return emptySet()
            val snapshot = home.activeUnreadSnapshot
            if (snapshot.displayCount == 0) return emptySet()
            val unread = snapshot.unread
            val bounds = listBounds ?: return emptySet()
            val layout = state.layoutInfo
            val (topPx, bottomPx) = readInsets
            val start = layout.viewportStartOffset + ceil(topPx).toInt()
            val end = layout.viewportStartOffset + floor(minOf(bounds.bottom - bottomPx, dockTop()) - bounds.top).toInt()
            val projection = home.feedProjection
            val divider = projection.dividerIndex
            return layout.visibleItemsInfo.mapNotNullTo(mutableSetOf()) { row ->
                if (row.index == divider) return@mapNotNullTo null
                val postIndex = row.index - if (divider != null && row.index > divider) 1 else 0
                val post = projection.visibleItems.getOrNull(postIndex) ?: return@mapNotNullTo null
                // A snapshot/layout race during a refresh or a fold must not acknowledge a
                // different post that happens to occupy the same index.
                post.statusId.takeIf {
                    post.id == row.key && it in unread &&
                        isTimelineRowMeaningfullyVisible(row.offset, row.size, start, end)
                }
            }
        }

        coroutineScope {
            val dwell = mutableMapOf<String, Job>()
            snapshotFlow { exposedStatusIds() }.collect { visible ->
                val left = dwell.keys - visible
                left.forEach { dwell.remove(it)?.cancel() }
                for (id in visible) {
                    if (id !in dwell) dwell[id] = launch {
                        delay(UnreadExposureMillis)
                        if (id in exposedStatusIds()) home.markUnreadViewed(scope, setOf(id))
                    }
                }
            }
        }
    }
}

/** Overlay chrome: retain the centered status label without shifting any timeline content. */
@Composable
internal fun TimelineUnreadHeader(
    count: Int,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box { center() }
            if (count > 0) {
                val spoken = stringResource(R.string.timeline_unread_count_accessibility, count)
                Text(
                    text = stringResource(R.string.timeline_unread_count, count),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
        },
    ) { children, constraints ->
        val width = constraints.maxWidth
        val edge = 16.dp.roundToPx().coerceAtMost(width / 4)
        val gap = 8.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val label = children[0].measure(loose)
        val countWidth = (width - 2 * edge).coerceAtLeast(0)
        val unread = children.getOrNull(1)?.measure(loose.copy(maxWidth = countWidth))
        val labelX = (width - label.width) / 2
        val unreadX = width - edge - (unread?.width ?: 0)
        val stacked = unread != null && label.width > 0 && unreadX < labelX + label.width + gap
        val height = if (stacked) label.height + gap + (unread?.height ?: 0)
            else maxOf(label.height, unread?.height ?: 0)
        val layoutHeight = height.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, layoutHeight) {
            label.place(labelX, if (stacked) 0 else (layoutHeight - label.height) / 2)
            unread?.place(unreadX, if (stacked) label.height + gap else (layoutHeight - unread.height) / 2)
        }
    }
}
