package com.example.myweibo

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myweibo.data.StoredWeiboAccount
import com.example.myweibo.ui.ViboDockAvatar
import com.example.myweibo.ui.ViboDockAvatarLoader
import com.example.myweibo.ui.ViboDockAvatarRequest
import com.example.myweibo.ui.resolveViboDockAvatarRequest
import com.kyant.backdrop.backdrops.emptyBackdrop
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViboDockAvatarComposeTest {
    @get:Rule val compose = createComposeRule()

    private val background = Color(0xFF172D49)
    private val firstColor = Color(0xFFF8422C)
    private val secondColor = Color(0xFF1ACB8B)
    // Reuse a URL across owners to prove the request resets even if the URL itself does not.
    private val first = ViboDockAvatarRequest("fixture-first", "https://example.invalid/avatar.jpg")
    private val second = first.copy(accountId = "fixture-second")
    private val failed = first.copy(accountId = "fixture-error")
    private val mine get() = compose.onNodeWithContentDescription("我的")
    private val photo get() = compose.onNodeWithTag("vibo_dock_avatar_image", useUnmergedTree = true)
    private val glass get() = compose.onNodeWithTag("vibo_dock_avatar_glass", useUnmergedTree = true)
    private val glyph get() = compose.onNodeWithTag("vibo_dock_avatar_fallback", useUnmergedTree = true)

    @Test fun successfulAvatarIsOnlyACircleAndSelectionOnlyChangesSemantics() {
        var selected by mutableStateOf(false)
        var clickCount = 0
        val loader = ViboDockAvatarLoader { solidAvatar(firstColor) }
        compose.setContent {
            MaterialTheme {
                Box(Modifier.background(background).padding(8.dp)) {
                    ViboDockAvatar(first, Color.White, selected, {
                        clickCount++
                        selected = true
                    }, emptyBackdrop(), 0f, 0f, loader = loader)
                }
            }
        }

        mine.assertIsNotSelected().assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
        photo.assertIsDisplayed().assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
        glass.assertDoesNotExist()
        glyph.assertDoesNotExist()
        val before = mine.captureToImage()
        val pixels = before.toPixelMap()
        assertEquals(firstColor, pixels[pixels.width / 2, pixels.height / 2])
        // A point inside the 44dp photo's square but outside its circle must remain unobscured.
        assertEquals(background, pixels[pixels.width / 6, pixels.height / 6])

        // Tap outside the photo, but within the original 56dp hit area.
        mine.performTouchInput { click(Offset(width / 20f, center.y)) }
        mine.assertIsSelected()
        compose.runOnIdle { assertEquals(1, clickCount) }
        val after = mine.captureToImage().toPixelMap()
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) assertEquals(pixels[x, y], after[x, y])
        }
    }

    @Test fun signedOutLoadingAndErrorShareFallbackAndSwitchNeverShowsPreviousAvatar() {
        var request by mutableStateOf<ViboDockAvatarRequest?>(null)
        val firstResult = CompletableDeferred<ImageBitmap?>()
        val secondResult = CompletableDeferred<ImageBitmap?>()
        val errorResult = CompletableDeferred<ImageBitmap?>()
        val loader = ViboDockAvatarLoader {
            when (it) {
                first -> firstResult.await()
                second -> secondResult.await()
                failed -> errorResult.await()
                else -> error("Unexpected fixture request: $it")
            }
        }
        compose.setContent {
            MaterialTheme {
                Box(Modifier.background(background).padding(8.dp)) {
                    ViboDockAvatar(request, Color.White, true, {}, emptyBackdrop(), 0f, 0f, loader = loader)
                }
            }
        }

        mine.assertIsSelected()
        assertFallback()
        compose.runOnIdle { request = first }
        assertFallback()
        compose.runOnIdle { firstResult.complete(solidAvatar(firstColor)) }
        assertAvatarColor(firstColor)

        compose.runOnIdle { request = second }
        assertFallback()
        compose.runOnIdle { secondResult.complete(solidAvatar(secondColor)) }
        assertAvatarColor(secondColor)

        compose.runOnIdle { request = failed }
        assertFallback()
        compose.runOnIdle { errorResult.complete(null) }
        assertFallback()
        compose.runOnIdle { request = null }
        assertFallback()
    }

    @Test fun avatarRequestRequiresVerifiedActiveOwnerAndSignedInState() {
        val accounts = listOf(
            StoredWeiboAccount(first.accountId, "Fixture first", first.url, emptyMap()),
            StoredWeiboAccount(second.accountId, "Fixture second", second.url, emptyMap()),
        )
        assertEquals(first, resolveViboDockAvatarRequest(first.accountId, first.accountId, true, null, accounts))
        assertNull(resolveViboDockAvatarRequest(first.accountId, first.accountId, false, null, accounts))
        assertNull(resolveViboDockAvatarRequest(null, first.accountId, true, null, accounts))
        assertNull(resolveViboDockAvatarRequest(first.accountId, second.accountId, true, null, accounts))
        assertEquals(second, resolveViboDockAvatarRequest(second.accountId, second.accountId, true, null, accounts))
    }

    private fun assertFallback() {
        photo.assertDoesNotExist()
        glass.assertIsDisplayed().assertWidthIsEqualTo(56.dp)
        glyph.assertIsDisplayed().assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
    }

    private fun assertAvatarColor(color: Color) {
        photo.assertIsDisplayed()
        glass.assertDoesNotExist()
        glyph.assertDoesNotExist()
        val pixels = photo.captureToImage().toPixelMap()
        assertEquals(color, pixels[pixels.width / 2, pixels.height / 2])
    }

    private fun solidAvatar(color: Color) = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(color.toArgb()) }
        .asImageBitmap()
}
