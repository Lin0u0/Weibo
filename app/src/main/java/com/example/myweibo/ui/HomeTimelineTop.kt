package com.example.myweibo.ui

import androidx.compose.foundation.lazy.LazyListState

/** Jump locally to the first actual post, not a synthetic row in the folded projection. */
internal fun HomeTimelineReadingState.goToTimelineTop(listState: LazyListState) {
    val firstPost = items.firstOrNull() ?: return
    longPostController(listState).suppressPagingForLayoutChange()
    if (feedProjection.visibleItems.firstOrNull()?.id != firstPost.id) {
        expandTimelineGap(listState, preserveViewport = false)
    }
    listState.requestScrollToItem(feedProjection.lazyIndexOfPost(0), 0)
}

/** Dock double tap starts the same explicit refresh as pull-to-refresh, without waiting for layout. */
internal fun HomeTimelineReadingState.goToTimelineTopAndRefresh(listState: LazyListState, refresh: () -> Unit) {
    goToTimelineTop(listState)
    refresh()
}
