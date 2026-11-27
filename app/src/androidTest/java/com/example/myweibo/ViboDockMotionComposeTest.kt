package com.example.myweibo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myweibo.ui.liquidglass.LiquidDockSearchButton
import com.example.myweibo.ui.liquidglass.ViboTimelinePickerReveal
import com.kyant.backdrop.backdrops.emptyBackdrop
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViboDockMotionComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchCanReleaseAtItsOriginalEdgeWhileVisuallyPressed() {
        val clicks = AtomicInteger()
        compose.setContent {
            MaterialTheme {
                LiquidDockSearchButton(
                    selected = false,
                    onClick = { clicks.incrementAndGet() },
                    backdrop = emptyBackdrop(),
                    frostAmount = 0f,
                    glassAmount = 0f,
                    forceDarkSurface = false,
                    modifier = Modifier.size(56.dp).testTag("search"),
                ) { Text("Search") }
            }
        }

        val search = compose.onNodeWithTag("search")
        search.performTouchInput { down(Offset(1f, center.y)) }
        compose.mainClock.advanceTimeBy(110)
        search.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, clicks.get()) }
    }

    @Test fun enteringPickerIsActionableAndClosingDropsItsSemanticsImmediately() {
        val clicks = AtomicInteger()
        var open by mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(180.dp, 240.dp)) {
                    if (open) {
                        ViboTimelinePickerReveal(lift = 4.dp, modifier = Modifier.align(Alignment.Center)) {
                            Text("Timeline option", Modifier.clickable {
                                clicks.incrementAndGet()
                                open = false
                            })
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { open = true }
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithText("Timeline option").assertHasClickAction().performTouchInput { click() }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Timeline option").assertDoesNotExist()
        assertEquals(1, clicks.get())
    }
}
