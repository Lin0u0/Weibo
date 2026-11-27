package com.example.myweibo

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myweibo.data.FeedItem
import com.example.myweibo.data.ReadingPreferences
import com.example.myweibo.data.ReadingPreferencesStore
import com.example.myweibo.data.TimelineCacheStore
import com.example.myweibo.data.TimelineKind
import com.example.myweibo.data.TimelinePage
import com.example.myweibo.data.ViboDockEntry
import com.example.myweibo.ui.FollowFeedScreen
import com.example.myweibo.ui.HomeTimelineReadingState
import com.example.myweibo.ui.MainTab
import com.example.myweibo.ui.ViboDockRuntime
import com.example.myweibo.ui.WeiboLiquidBottomBar
import com.example.myweibo.ui.goToTimelineTop
import com.kyant.backdrop.backdrops.emptyBackdrop
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViboDockTimelineTopComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectedHomeSingleTapJumpsToFirstPostWithoutRefreshOrPaging() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val isolatedContext = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                baseContext.getSharedPreferences("dock_timeline_top_$name", mode)
        }
        val accountId = "2143"
        val kind = TimelineKind.Following
        val rows = (24 downTo 1).map(::post)
        val newest = rows.first()
        val timeline = HomeTimelineReadingState(
            ReadingPreferencesStore(isolatedContext), TimelineCacheStore(context), kind,
            ReadingPreferences(reverseReading = true, newPostLimit = 1),
        ).apply {
            timelineCacheAccountId = accountId
            feedCacheLoaded = true
            feedInitialScrollReconciled = true
            timelineVerifiedFromNetwork = true
            items = rows.drop(3)
            nextCursor = "fixture-next-page"
            acceptOrderedRows(accountId, chronological = true)
        }
        lateinit var state: LazyListState
        var refreshes = 0
        var pages = 0
        val singleTaps = AtomicInteger()
        var doubleTaps = 0
        compose.setContent {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 5)
            SideEffect { state = listState }
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    FollowFeedScreen(
                        listState = listState,
                        homeTimeline = timeline,
                        hasLoginCookie = true,
                        onRefresh = { refreshes++ },
                        onLoadMore = { pages++ },
                        onOpenLoginSettings = {},
                        onItemClick = { _, _ -> },
                        onCommentClick = { _, _ -> },
                        onCommentLongClick = {},
                        onRepostClick = { _, _ -> },
                        onMediaClick = { _, _ -> },
                    )
                    WeiboLiquidBottomBar(
                        selectedTab = MainTab.Feed,
                        onTabChange = {},
                        backdrop = emptyBackdrop(),
                        timelineMenuExpanded = false,
                        onTimelineMenuExpandedChange = {},
                        timelineMenuContent = {},
                        dockRuntime = ViboDockRuntime(
                            catalogue = emptyList(),
                            onEntrySelected = {},
                            profileAvatar = null,
                            onSelectedTimelineClick = { singleTaps.incrementAndGet(); timeline.goToTimelineTop(listState) },
                            onEntryDoubleClick = { doubleTaps++ },
                        ),
                        modifier = Modifier.align(Alignment.BottomCenter),
                        glassAmount = 0f,
                    )
                }
            }
        }

        compose.runOnIdle {
            val baseline = requireNotNull(timeline.captureExplicitRefreshBaseline(accountId))
            assertEquals(3, timeline.publishRefresh(
                TimelinePage(rows.take(4)), state, protectViewport = true, keepReading = true, explicitBaseline = baseline,
            ))
            state.requestScrollToItem(10, 19)
        }
        val pagesBeforeTap = compose.runOnIdle {
            assertEquals(2, timeline.feedProjection.gap?.hiddenPostCount)
            assertFalse(timeline.reverseBrowseGapExpanded)
            assertTrue(state.firstVisibleItemIndex > 0)
            assertFalse(state.layoutInfo.visibleItemsInfo.any { it.key == newest.id })
            pages
        }
        compose.onNodeWithContentDescription(ViboDockEntry.Home.title)
            .assertIsSelected().performTouchInput { click() }
        compose.waitUntil(2_000) { singleTaps.get() == 1 }
        compose.runOnIdle {
            assertEquals(0, state.firstVisibleItemIndex)
            assertEquals(0, state.firstVisibleItemScrollOffset)
            assertEquals(newest.id, state.layoutInfo.visibleItemsInfo.first().key)
            assertEquals(2, timeline.feedProjection.gap?.hiddenPostCount)
            assertFalse(timeline.reverseBrowseGapExpanded)
            assertTrue(timeline.longPostController(state).isLayoutPagingSuppressed)
            assertEquals("fixture-next-page", timeline.nextCursor)
            assertEquals(0, refreshes)
            assertEquals(pagesBeforeTap, pages)
            assertEquals(0, doubleTaps)
        }
    }

    private fun post(number: Int) = FeedItem(
        id = "dock-top-row-$number",
        statusId = "dock-top-status-$number",
        authorId = "fixture-author",
        authorName = "Dock test author",
        authorAvatarUrl = null,
        createdAt = (1_700_000_000L + number).toString(),
        source = null,
        ipLocation = null,
        text = "Dock top post $number",
        repostsCount = "0",
        commentsCount = "0",
        likesCount = "0",
        images = emptyList(),
    )
}
