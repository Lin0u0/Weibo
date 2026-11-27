package com.example.myweibo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myweibo.ui.MainTab
import com.example.myweibo.ui.SettingsAboutCard
import com.example.myweibo.ui.SettingsList
import com.example.myweibo.ui.WeiboLiquidBottomBar
import com.kyant.backdrop.backdrops.emptyBackdrop
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDockScrollComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun viewportExtendsBehindDockAndLastCardAndItsActionClearIt() {
        val precedingRows = 20
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    SettingsList { bottomContentPadding ->
                        items(precedingRows) { index ->
                            Box(Modifier.fillMaxWidth().height(64.dp)) { Text("Setting $index") }
                        }
                        item {
                            SettingsAboutCard(versionName = "fixture", bottomContentPadding = bottomContentPadding)
                        }
                    }
                    WeiboLiquidBottomBar(
                        selectedTab = MainTab.Mine,
                        backdrop = emptyBackdrop(),
                        timelineMenuExpanded = false,
                        onTimelineMenuExpandedChange = {},
                        timelineMenuContent = {},
                        onTabChange = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }

        val settings = compose.onNode(hasScrollToIndexAction())
        val dock = compose.onNodeWithContentDescription(MainTab.Mine.label).assertIsDisplayed()
        val dockBounds = dock.fetchSemanticsNode().boundsInRoot
        assertTrue(
            "The settings viewport should extend behind the entire dock, not end above it",
            settings.fetchSemanticsNode().boundsInRoot.bottom > dockBounds.bottom,
        )

        settings.performScrollToIndex(precedingRows)
        val about = compose.onNode(hasClickAction() and hasText("关于")).assertIsDisplayed()
        assertTrue(
            "The final settings row should clear the dock",
            about.fetchSemanticsNode().boundsInRoot.bottom < dockBounds.top,
        )
        about.performTouchInput { click() }

        // Expanding the last row should reveal its action without an extra scroll gesture.
        val github = compose.onNode(hasClickAction() and hasText("GitHub 主页")).assertIsDisplayed()
        assertTrue(
            "The expanded action should clear the dock",
            github.fetchSemanticsNode().boundsInRoot.bottom < dockBounds.top,
        )
    }
}
