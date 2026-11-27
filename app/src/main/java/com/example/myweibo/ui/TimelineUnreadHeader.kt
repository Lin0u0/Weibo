package com.example.myweibo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myweibo.data.isTimelineRowMeaningfullyVisible
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor

private const val ReadingExposureMillis = 450L

@Composable
internal fun ObserveTimelineReadingBoundary(
    home: HomeTimelineReadingState,
    state: LazyListState,
    exposed: Boolean,
    topInsetPx: Float,
    dockTopInRoot: () -> Float,
) {
    val scope = home.readingScope ?: return
    if (!exposed || !home.feedInitialScrollReconciled) return
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
    LaunchedEffect(home, scope, state, lifecycle, home.readingExposureGeneration) {
        fun exposedRowIds(): Set<String> {
            if (lifecycleState != Lifecycle.State.RESUMED || home.readingScope != scope ||
                !home.feedInitialScrollReconciled || home.isLoading || state.isScrollInProgress
            ) return emptySet()
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
                post.id.takeIf {
                    it == row.key && !post.isPinned && home.canObserveReadingRow(it) &&
                        isTimelineRowMeaningfullyVisible(row.offset, row.size, start, end)
                }
            }
        }

        coroutineScope {
            val dwell = mutableMapOf<String, Job>()
            val qualified = mutableSetOf<String>()
            snapshotFlow { exposedRowIds() }.collect { visible ->
                val left = dwell.keys - visible
                left.forEach { dwell.remove(it)?.cancel() }
                qualified.retainAll(visible)
                for (id in visible) {
                    if (id !in dwell) dwell[id] = launch {
                        delay(ReadingExposureMillis)
                        val stillExposed = exposedRowIds()
                        if (id in stillExposed) {
                            qualified += id
                            home.markReadingRowsViewed(scope, qualified.intersect(stillExposed))
                        }
                    }
                }
            }
        }
    }
}

/** Center the existing status capsule; the count now belongs to the list rail. */
@Composable
internal fun TimelineUnreadHeader(
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { center() }
}
