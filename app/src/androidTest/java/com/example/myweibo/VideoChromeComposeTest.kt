package com.example.myweibo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myweibo.ui.LocalVideoChromeStyle
import com.example.myweibo.ui.VideoChromeButton
import com.example.myweibo.ui.VideoChromeStyle
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class VideoChromeComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun playerButtonRespondsToPreferencesWithoutLosingItsTapTarget() {
        val style = mutableStateOf(VideoChromeStyle(0f, 0f))
        val backdropDraws = AtomicInteger()
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                Box(Modifier.background(Color.White).padding(8.dp)) {
                    val backdrop = rememberCanvasBackdrop {
                        backdropDraws.incrementAndGet()
                        drawRect(Color.White)
                    }
                    CompositionLocalProvider(LocalVideoChromeStyle provides style.value) {
                        VideoChromeButton(
                            onClick = { clicks++ },
                            backdrop = backdrop,
                            modifier = Modifier.size(48.dp).testTag("video-button"),
                        ) {}
                    }
                }
            }
        }

        val button = compose.onNodeWithTag("video-button")
        val flat = button.captureToImage().toPixelMap()
        assertTrue(flat[flat.width / 2, flat.height / 2].red < 0.35f)
        assertEquals(0, backdropDraws.get())
        button.performTouchInput { click() }

        compose.runOnIdle { style.value = VideoChromeStyle(0.5f, 0f) }
        button.captureToImage()
        assertTrue(backdropDraws.get() > 0)
        button.performTouchInput { click() }
        compose.runOnIdle { assertEquals(2, clicks) }
    }
}
