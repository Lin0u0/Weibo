package com.example.myweibo

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myweibo.data.StoredWeiboAccount
import com.example.myweibo.ui.ViboDockAvatar
import com.example.myweibo.ui.ViboDockAvatarLoader
import com.example.myweibo.ui.ViboDockAvatarRequest
import com.example.myweibo.ui.liquidglass.LiquidDockSearchButton
import com.example.myweibo.ui.resolveViboDockAvatarRequest
import com.example.myweibo.ui.theme.MyWeiboTheme
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.isRenderEffectSupported
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ViboDockAvatarComposeTest {
    private val motionScale = object : MotionDurationScale {
        override var scaleFactor: Float = 1f
    }
    @get:Rule val compose = createComposeRule(effectContext = motionScale)

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

    @Test fun photoIsCroppedInsideOneSurfaceAndSelectionTintsOnlyTheShell() {
        var selected by mutableStateOf(false)
        var clickCount = 0
        val primary = Color(0xFF3447AF)
        val loader = ViboDockAvatarLoader { croppedAvatar() }
        compose.setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = primary)) {
                Box(Modifier.background(background).padding(8.dp)) {
                    ViboDockAvatar(first, Color.Black, selected, { clickCount++ }, emptyBackdrop(), 0f, 0f, loader = loader)
                }
            }
        }

        mine.assertIsNotSelected().assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
        assertSingleSurface()
        photo.assertIsDisplayed().assertWidthIsEqualTo(42.dp).assertHeightIsEqualTo(42.dp)
        glyph.assertDoesNotExist()
        val before = mine.captureToImage().toPixelMap()
        assertEquals(firstColor, before.centerPixel())
        assertEquals(firstColor, before[before.width / 5, before.height / 2])
        // Outside the photo's circle but inside both its square and the endcap circle.
        assertEquals(Color.White, before[before.width / 5, before.height / 5])
        assertEquals(background, before[0, 0])
        val shellX = before.width / 12
        assertEquals(Color.White, before[shellX, before.height / 2])

        // Toggle selection directly so the capture is not sampling a touch ripple.
        compose.runOnIdle { selected = true }
        mine.assertIsSelected()
        assertSingleSurface()
        val after = mine.captureToImage().toPixelMap()
        assertEquals(firstColor, after.centerPixel())
        assertEquals(background, after[0, 0])
        assertNearColor(primary.copy(alpha = 0.10f).compositeOver(Color.White), after[shellX, after.height / 2])

        mine.performTouchInput { click(Offset(width / 20f, center.y)) }
        compose.runOnIdle { assertEquals(1, clickCount) }
    }

    @Test fun loadedAvatarUsesTheSameGlassAsSearch() {
        assumeTrue(isRenderEffectSupported())
        val loader = ViboDockAvatarLoader { solidAvatar(firstColor) }
        compose.setContent {
            MaterialTheme {
                val backdrop = rememberCanvasBackdrop { drawRect(background) }
                Row(Modifier.background(background).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiquidDockSearchButton(false, {}, backdrop, 0.5f, 0.5f, false, Modifier.size(56.dp).testTag("avatar_fixture_search")) {}
                    ViboDockAvatar(first, Color.Black, false, {}, backdrop, 0.5f, 0.5f, loader = loader)
                }
            }
        }

        assertAvatarColor(firstColor)
        val avatarPixels = mine.captureToImage().toPixelMap()
        val searchPixels = compose.onNodeWithTag("avatar_fixture_search").captureToImage().toPixelMap()
        val shellX = avatarPixels.width / 12
        val centerY = avatarPixels.height / 2
        assertTrue(avatarPixels[shellX, centerY] != background)
        assertTrue(avatarPixels[shellX, centerY] != Color.White)
        assertNearColor(searchPixels[shellX, centerY], avatarPixels[shellX, centerY])
    }

    @Test fun fallbackGlyphHasContrastOnLightAndDarkOpaqueSurfaces() {
        var dark by mutableStateOf(false)
        var selected by mutableStateOf(false)
        compose.setContent {
            MyWeiboTheme(darkTheme = dark) {
                val glyphColor = if (dark) Color(0xFFD0D0D0) else Color.Black
                Box(Modifier.background(background).padding(8.dp)) {
                    ViboDockAvatar(null, glyphColor, selected, {}, emptyBackdrop(), 1f, 1f)
                }
            }
        }

        for (darkTheme in listOf(false, true)) {
            for (isSelected in listOf(false, true)) {
                compose.runOnIdle {
                    dark = darkTheme
                    selected = isSelected
                }
                assertFallback()
                val pixels = mine.captureToImage().toPixelMap()
                val shell = pixels[pixels.width / 12, pixels.height / 2]
                if (!isSelected) assertEquals(if (darkTheme) Color(0xFF121212) else Color.White, shell)
                val expectedGlyph = if (darkTheme) Color(0xFFD0D0D0) else Color.Black
                val renderedGlyph = (0 until pixels.height).asSequence()
                    .flatMap { y -> (0 until pixels.width).asSequence().map { x -> pixels[x, y] } }
                    .firstOrNull { it == expectedGlyph }
                checkNotNull(renderedGlyph) { "Expected an opaque glyph stroke for dark=$darkTheme" }
                val tones = listOf(renderedGlyph.luminance(), shell.luminance())
                assertTrue((tones.max() + 0.05f) / (tones.min() + 0.05f) >= 3f)
            }
        }
    }

    @Test fun reducedMotionKeepsPhotoSizeWhileHeldAndReleasesAsAClick() {
        motionScale.scaleFactor = 0f
        var clicks = 0
        val loader = ViboDockAvatarLoader { solidAvatar(firstColor) }
        compose.setContent {
            MaterialTheme {
                ViboDockAvatar(first, Color.Black, false, { clicks++ }, emptyBackdrop(), 0f, 0f, loader = loader)
            }
        }

        assertAvatarColor(firstColor)
        val rest = mine.captureToImage().toPixelMap().photoWidthAtCenter()
        mine.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200)
        val held = mine.captureToImage().toPixelMap().photoWidthAtCenter()
        assertTrue("Reduced motion changed photo width from $rest to $held pixels", kotlin.math.abs(rest - held) <= 1)
        mine.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun signedOutLoadingAndErrorShareFallbackAndSwitchNeverShowsPreviousAvatar() {
        var request by mutableStateOf<ViboDockAvatarRequest?>(null)
        val firstResult = CompletableDeferred<ImageBitmap?>()
        val secondResult = CompletableDeferred<ImageBitmap?>()
        val stale = first.copy(accountId = "fixture-stale")
        val staleResult = CompletableDeferred<ImageBitmap?>()
        val errorResult = CompletableDeferred<ImageBitmap?>()
        val loader = ViboDockAvatarLoader {
            when (it) {
                first -> firstResult.await()
                second -> secondResult.await()
                stale -> staleResult.await()
                failed -> errorResult.await()
                else -> error("Unexpected fixture request: $it")
            }
        }
        compose.setContent {
            MaterialTheme {
                Box(Modifier.background(background).padding(8.dp)) {
                    ViboDockAvatar(request, Color.Black, true, {}, emptyBackdrop(), 0f, 0f, loader = loader)
                }
            }
        }

        mine.assertIsSelected()
        assertFallback()
        compose.runOnIdle { request = first }
        assertFallback()
        compose.runOnIdle { firstResult.complete(solidAvatar(firstColor)) }
        assertAvatarColor(firstColor)

        compose.runOnIdle { request = stale }
        assertFallback()
        compose.runOnIdle { request = second }
        assertFallback()
        // The previous owner's delayed completion must not replace this owner's loading state.
        compose.runOnIdle { staleResult.complete(solidAvatar(firstColor)) }
        assertFallback()
        compose.runOnIdle { secondResult.complete(solidAvatar(secondColor)) }
        assertAvatarColor(secondColor)

        compose.runOnIdle { request = failed }
        assertFallback()
        compose.runOnIdle { errorResult.complete(null) }
        assertFallback()
        compose.runOnIdle { request = second }
        assertAvatarColor(secondColor)
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
        assertSingleSurface()
        glyph.assertIsDisplayed().assertWidthIsEqualTo(24.dp).assertHeightIsEqualTo(24.dp)
    }

    private fun assertAvatarColor(color: Color) {
        photo.assertIsDisplayed()
        assertSingleSurface()
        glyph.assertDoesNotExist()
        val pixels = photo.captureToImage().toPixelMap()
        assertEquals(color, pixels[pixels.width / 2, pixels.height / 2])
    }

    private fun assertSingleSurface() {
        compose.onAllNodesWithTag("vibo_dock_avatar_glass", useUnmergedTree = true).assertCountEquals(1)
        glass.assertIsDisplayed().assertWidthIsEqualTo(56.dp).assertHeightIsEqualTo(56.dp)
    }

    private fun PixelMap.centerPixel() = this[width / 2, height / 2]

    private fun PixelMap.photoWidthAtCenter() = (0 until width).count { x ->
        val pixel = this[x, height / 2]
        pixel.red > pixel.green + 0.3f && pixel.red > pixel.blue + 0.3f
    }

    private fun assertNearColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, 0.01f)
        assertEquals(expected.green, actual.green, 0.01f)
        assertEquals(expected.blue, actual.blue, 0.01f)
    }

    // The green ends would appear if the wide image were fitted instead of center-cropped.
    private fun croppedAvatar() = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888).apply {
        eraseColor(secondColor.toArgb())
        for (x in 32 until 96) for (y in 0 until height) setPixel(x, y, firstColor.toArgb())
    }.asImageBitmap()

    private fun solidAvatar(color: Color) = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(color.toArgb()) }
        .asImageBitmap()
}
