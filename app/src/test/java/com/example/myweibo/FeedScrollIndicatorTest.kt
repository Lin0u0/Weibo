package com.example.myweibo

import androidx.compose.foundation.lazy.LazyListItemInfo
import com.example.myweibo.ui.FeedScrollRows
import com.example.myweibo.ui.FeedScrollThumb
import com.example.myweibo.ui.feedPostsAboveViewport
import com.example.myweibo.ui.feedRowPosition
import com.example.myweibo.ui.feedScrollLabelTop
import com.example.myweibo.ui.feedScrollRailBottom
import com.example.myweibo.ui.feedScrollThumb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedScrollIndicatorTest {
    @Test fun hidesForEmptyOrNonScrollableListsAndWhenInsetsLeaveNoRail() {
        assertNull(thumb(count = 0, start = 0f, end = 0f))
        assertNull(thumb(count = 3, start = 0f, end = 3f, backward = false, forward = false))
        assertNull(thumb(count = 10, start = 2f, end = 4f, rail = 0f))
    }

    @Test fun partialRowsContributeByFractionAndMoveDownInLayoutOrder() {
        val start = feedRowPosition(index = 2, offset = -100, height = 200, edge = 0)
        val end = feedRowPosition(index = 5, offset = 260, height = 80, edge = 300)
        assertEquals(2.5f, start, 0f)
        assertEquals(5.5f, end, 0f)
        val before = requireNotNull(thumb(count = 10, start = start, end = end))
        val after = requireNotNull(thumb(count = 10, start = 3f, end = 6f))
        assertEquals(80f * 2.5f / 7f, before.top, 0.001f)
        assertEquals(20f, before.height, 0f)
        assertEquals(before.height, after.height, 0f)
        assertTrue(after.top > before.top)
    }

    @Test fun endpointsAlignAndReverseLayoutMirrorsOnlyThePosition() {
        val top = requireNotNull(thumb(count = 40, start = 0f, end = 3f, backward = false))
        val bottom = requireNotNull(thumb(count = 40, start = 37f, end = 40f, forward = false))
        val reversed = requireNotNull(
            thumb(count = 40, start = 0f, end = 3f, backward = false, reverseLayout = true),
        )
        assertEquals(20f, top.height, 0f)
        assertEquals(0f, top.top, 0f)
        assertEquals(100f, bottom.top + bottom.height, 0f)
        assertEquals(bottom, reversed)
    }

    @Test fun oneTallRowStillHasScrollableProgress() {
        val start = feedRowPosition(index = 0, offset = -100, height = 400, edge = 0)
        val end = feedRowPosition(index = 0, offset = -100, height = 400, edge = 200)
        val thumb = requireNotNull(thumb(count = 1, start = start, end = end))
        assertEquals(40f, thumb.top, 0.001f)
        assertEquals(20f, thumb.height, 0f)
    }

    @Test fun newlyLaidOutRowsChangeTheRangeWithoutChangingTheAnchor() {
        val initial = requireNotNull(thumb(count = 8, start = 4f, end = 8f, forward = false))
        val appended = requireNotNull(thumb(count = 12, start = 4f, end = 8f))
        assertEquals(100f, initial.top + initial.height, 0f)
        assertEquals(initial.height, appended.height, 0f)
        assertTrue(appended.top + appended.height < 100f)
    }

    @Test fun fixedThumbOnlyShrinksForTinyRailAndLabelStaysBesideIt() {
        val tiny = requireNotNull(thumb(count = 15, start = 5f, end = 6f, rail = 12f))
        assertEquals(FeedScrollThumb(0f, 12f), tiny)
        assertNull(feedScrollLabelTop(8f, 12f, tiny, labelHeight = 16f))
        assertEquals(8f, requireNotNull(feedScrollLabelTop(8f, 100f, FeedScrollThumb(0f, 20f), 30f)), 0f)
        assertEquals(78f, requireNotNull(feedScrollLabelTop(8f, 100f, FeedScrollThumb(80f, 20f), 30f)), 0f)
    }

    @Test fun unequalRowHeightsChangeThumbPositionButNotHeight() {
        val startTall = feedRowPosition(index = 3, offset = -20, height = 200, edge = 0)
        val startShort = feedRowPosition(index = 3, offset = -20, height = 40, edge = 0)
        val tall = requireNotNull(thumb(count = 12, start = startTall, end = 4.5f))
        val short = requireNotNull(thumb(count = 12, start = startShort, end = 7f))
        assertTrue(tall.top != short.top)
        assertEquals(20f, tall.height, 0f)
        assertEquals(tall.height, short.height, 0f)
    }

    @Test fun hiddenDockRestoresRailToTheSystemInset() {
        fun bottom(dockTop: Float) = feedScrollRailBottom(500f, 24f, 40f, dockTop)
        assertEquals(410f, bottom(450f), 0f)
        assertEquals(476f, bottom(Float.POSITIVE_INFINITY), 0f)
        assertEquals(476f, bottom(530f), 0f)
    }

    @Test fun normalLayoutCountsOnlyPostsFullyAboveThePaddedAndOccludedTop() {
        val rows = FeedScrollRows(postCount = 3, dividerIndex = 1)
        val visible = listOf(row(1, -25, 10), row(2, -15, 31), row(3, 16, 20), row(4, 36, 20))
        // Index zero isn't laid out; index one is a marker and index four a loading footer.
        assertEquals(1, feedPostsAboveViewport(rows, visible, viewportEdge = -5f))
        assertEquals(1, feedPostsAboveViewport(rows, visible, viewportEdge = 15f))
        assertEquals(2, feedPostsAboveViewport(rows, visible, viewportEdge = 16f))
        assertEquals(3, feedPostsAboveViewport(rows, visible, viewportEdge = 37f))
        assertEquals(3, feedPostsAboveViewport(rows, listOf(row(5, 0, 20)), viewportEdge = 0f))
        assertNull(feedPostsAboveViewport(rows, emptyList(), viewportEdge = 0f))
    }

    @Test fun n3N2N1RR2CountsTheCollapsedBlockOnlyAfterItsDividerLeavesTheViewport() {
        // Collapsed order is N3, divider(N2, N1), R, R2, footer.
        val collapsed = FeedScrollRows(postCount = 3, dividerIndex = 1, collapsedPostCount = 2)
        val collapsedLayout = listOf(row(0, -65, 40), row(1, -25, 26), row(2, 1, 50), row(3, 51, 50))
        assertEquals(1, feedPostsAboveViewport(collapsed, collapsedLayout, viewportEdge = 0f))
        assertEquals(3, feedPostsAboveViewport(collapsed, collapsedLayout, viewportEdge = 1f))
        assertEquals(3, feedPostsAboveViewport(collapsed, collapsedLayout.drop(2), viewportEdge = 1f))
        // Scroll back to the partly visible divider.
        assertEquals(1, feedPostsAboveViewport(collapsed, collapsedLayout, viewportEdge = 0f))
        // Expanded order is N3, divider, N2, N1, R, R2, footer.
        val expanded = FeedScrollRows(postCount = 5, dividerIndex = 1)
        val expandedLayout = listOf(row(2, -50, 51), row(3, 1, 50), row(4, 51, 50), row(5, 101, 40))
        assertEquals(1, feedPostsAboveViewport(expanded, expandedLayout, viewportEdge = 0f))
        assertEquals(2, feedPostsAboveViewport(expanded, expandedLayout, viewportEdge = 1f))
        assertEquals(3, feedPostsAboveViewport(expanded, expandedLayout, viewportEdge = 51f))
        assertEquals(3, feedPostsAboveViewport(expanded.copy(postCount = 12), expandedLayout, viewportEdge = 51f))
    }

    @Test fun reversedLayoutCountsFromThePhysicalTopAndExcludesAnIntersectingDivider() {
        val rows = FeedScrollRows(postCount = 5, dividerIndex = 3, collapsedPostCount = 2)
        val visible = listOf(row(1, 100, 50), row(2, 150, 50), row(3, 200, 25), row(4, 225, 50))
        assertEquals(2, feedPostsAboveViewport(rows, visible, viewportEdge = 201f, reverseLayout = true))
        assertEquals(4, feedPostsAboveViewport(rows, visible, viewportEdge = 200f, reverseLayout = true))
        assertEquals(0, feedPostsAboveViewport(rows, listOf(row(5, 300, 40)), viewportEdge = 340f, reverseLayout = true))
    }

    private fun row(index: Int, offset: Int, size: Int): LazyListItemInfo = object : LazyListItemInfo {
        override val index = index
        override val offset = offset
        override val size = size
        override val key: Any = index
        override val contentType: Any? = null
    }

    private fun thumb(
        count: Int,
        start: Float,
        end: Float,
        backward: Boolean = true,
        forward: Boolean = true,
        reverseLayout: Boolean = false,
        rail: Float = 100f,
    ) = feedScrollThumb(count, start, end, backward, forward, reverseLayout, rail, thumbHeight = 20f)
}
