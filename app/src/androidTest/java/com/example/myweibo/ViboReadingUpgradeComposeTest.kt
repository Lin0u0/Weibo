package com.example.myweibo

import android.content.Context
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
import com.example.myweibo.ui.FollowFeedScreen
import com.example.myweibo.ui.HomeTimelineReadingState
import com.example.myweibo.ui.MainTab
import com.example.myweibo.ui.ViboDockRuntime
import com.example.myweibo.ui.WeiboLiquidBottomBar
import com.example.myweibo.ui.goToTimelineTop
import com.kyant.backdrop.backdrops.emptyBackdrop
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Seed historic records using only test code. The current runtime ignores and preserves both formats. */
@RunWith(AndroidJUnit4::class)
class ViboReadingUpgradeComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun legacyAndV2ReadingRecordsRemainUntouchedAndDoNotAffectDockOrFolding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = "215721612162"
        val kind = TimelineKind.Following
        val rows = (24 downTo 1).map(::post)
        val midList = rows[10]
        val oldBatchMember = rows[2]
        val oldBatchHead = rows[3]
        val key = MessageDigest.getInstance("SHA-256").digest("$owner/kind:${kind.name}".toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val v1 = context.getSharedPreferences("timeline_reading_boundary_v1", Context.MODE_PRIVATE)
        val v2 = context.getSharedPreferences("timeline_reading_boundary_v2", Context.MODE_PRIVATE)
        assertTrue(v1.edit().putString("$key.row", midList.id).putString("$key.status", midList.statusId).commit())
        val historicBatch = """{"baseline":{"row":"${oldBatchHead.id}","status":"${oldBatchHead.statusId}"},"remaining":[{"row":"${oldBatchMember.id}","status":"${oldBatchMember.statusId}"}]}"""
        assertTrue(v2.edit().putString("$key.refresh_batch", historicBatch).commit())
        val v1Before = v1.all.toMap()
        val v2Before = v2.all.toMap()

        val timeline = HomeTimelineReadingState(
            ReadingPreferencesStore(context), TimelineCacheStore(context), kind,
            ReadingPreferences(reverseReading = true, newPostLimit = 1),
        ).apply {
            timelineCacheAccountId = owner
            feedCacheLoaded = true
            items = rows
            acceptOrderedRows(owner, chronological = true)
        }
        val singles = AtomicInteger()
        val unexpectedActions = AtomicInteger()
        lateinit var scroll: LazyListState
        compose.setContent {
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = 9, initialFirstVisibleItemScrollOffset = 19)
            SideEffect { scroll = listState }
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    FollowFeedScreen(
                        listState = listState,
                        homeTimeline = timeline,
                        hasLoginCookie = true,
                        onRefresh = { unexpectedActions.incrementAndGet() },
                        onLoadMore = { unexpectedActions.incrementAndGet() },
                        onOpenLoginSettings = {},
                        onItemClick = { _, _ -> },
                        onCommentClick = { _, _ -> },
                        onCommentLongClick = {},
                        onRepostClick = { _, _ -> },
                        onMediaClick = { _, _ -> },
                    )
                    WeiboLiquidBottomBar(
                        selectedTab = MainTab.Feed,
                        backdrop = emptyBackdrop(),
                        timelineMenuExpanded = false,
                        onTimelineMenuExpandedChange = {},
                        timelineMenuContent = {},
                        onTabChange = { unexpectedActions.incrementAndGet() },
                        selectedTimelineKind = kind,
                        dockRuntime = ViboDockRuntime(
                            catalogue = emptyList(),
                            onEntrySelected = { unexpectedActions.incrementAndGet() },
                            onEntryDoubleClick = { unexpectedActions.incrementAndGet() },
                            onSelectedTimelineClick = {
                                timeline.goToTimelineTop(listState)
                                singles.incrementAndGet()
                            },
                            profileAvatar = null,
                        ),
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        compose.runOnIdle {
            assertNull(timeline.confirmedRefresh)
            assertNull(timeline.feedProjection.dividerIndex)
            assertEquals(9, scroll.firstVisibleItemIndex)
            assertEquals(rows[9].id, scroll.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 9 }?.key)
        }
        compose.onNodeWithContentDescription(MainTab.Feed.label).assertIsSelected().performTouchInput { click() }
        compose.waitUntil(timeoutMillis = 2_000) { singles.get() == 1 }
        compose.runOnIdle {
            assertEquals(0, scroll.firstVisibleItemIndex)
            assertEquals(rows.first().id, scroll.layoutInfo.visibleItemsInfo.firstOrNull()?.key)
            assertEquals(0, scroll.firstVisibleItemScrollOffset)
            assertEquals(0, unexpectedActions.get())
            val baseline = requireNotNull(timeline.captureExplicitRefreshBaseline(owner))
            assertEquals(2, timeline.publishRefresh(
                TimelinePage(listOf(post(26), post(25), rows.first())), scroll,
                protectViewport = true, keepReading = true, explicitBaseline = baseline, returnToTop = true,
            ))
            assertEquals(1, timeline.feedProjection.gap?.hiddenPostCount)
        }
        assertEquals(v1Before, v1.all)
        assertEquals(v2Before, v2.all)
    }

    private fun post(number: Int) = FeedItem(
        id = "upgrade-post-$number", statusId = "upgrade-status-$number",
        authorId = "fixture-author", authorName = "Fixture author", authorAvatarUrl = null,
        createdAt = (1_700_000_000L + number).toString(), source = null, ipLocation = null,
        text = "Offline upgrade fixture post $number", repostsCount = "0", commentsCount = "0",
        likesCount = "0", images = emptyList(),
    )
}
