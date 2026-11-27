package com.example.myweibo

import com.example.myweibo.ui.feedRowPosition
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
        assertEquals(25f, before.top, 0.001f)
        assertEquals(30f, before.height, 0.001f)
        assertTrue(after.top > before.top)
    }

    @Test fun endpointsAlignAndReverseLayoutMirrorsOnlyThePosition() {
        val top = requireNotNull(thumb(count = 40, start = 0f, end = 3f, backward = false))
        val bottom = requireNotNull(thumb(count = 40, start = 37f, end = 40f, forward = false))
        val reversed = requireNotNull(
            thumb(count = 40, start = 0f, end = 3f, backward = false, reverseLayout = true),
        )
        assertEquals(24f, top.height, 0f)
        assertEquals(0f, top.top, 0f)
        assertEquals(100f, bottom.top + bottom.height, 0f)
        assertEquals(bottom, reversed)
    }

    @Test fun oneTallRowStillHasScrollableProgress() {
        val start = feedRowPosition(index = 0, offset = -100, height = 400, edge = 0)
        val end = feedRowPosition(index = 0, offset = -100, height = 400, edge = 200)
        val thumb = requireNotNull(thumb(count = 1, start = start, end = end))
        assertEquals(25f, thumb.top, 0.001f)
        assertEquals(50f, thumb.height, 0.001f)
    }

    @Test fun newlyLaidOutRowsChangeTheRangeWithoutChangingTheAnchor() {
        val initial = requireNotNull(thumb(count = 8, start = 4f, end = 8f, forward = false))
        val appended = requireNotNull(thumb(count = 12, start = 4f, end = 8f))
        assertEquals(100f, initial.top + initial.height, 0f)
        assertTrue(appended.height < initial.height)
        assertTrue(appended.top + appended.height < 100f)
    }

    private fun thumb(
        count: Int,
        start: Float,
        end: Float,
        backward: Boolean = true,
        forward: Boolean = true,
        reverseLayout: Boolean = false,
        rail: Float = 100f,
    ) = feedScrollThumb(count, start, end, backward, forward, reverseLayout, rail, minThumbHeight = 24f)
}
