package com.example.myweibo

import com.example.myweibo.data.ProfileCoverState
import com.example.myweibo.data.WeiboJsonParser
import com.example.myweibo.data.selectCurrentProfileCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentProfileCandidateTest {
    private val cover = "https://wx3.sinaimg.cn/large/cover.jpg?sig=synthetic"
    private val config = JSONObject().put("uid", "42").put("screen_name", "Config name")
    private val desktopOmission = """{"ok":1,"data":{"user":{"idstr":"42","screen_name":"Desktop"}}}"""
    private val rejectedDesktop = """{"ok":0,"data":{"user":{"idstr":"42","screen_name":"Error body"}}}"""
    private val mobileCover = """{"ok":1,"data":{"userInfo":{"id":42,"screen_name":"Mobile","cover_image_phone":"$cover"}}}"""
    private val explicitClear = """{"ok":1,"data":{"user":{"idstr":"42","cover_image_phone":null,"cover_image":" "}}}"""

    @Test fun verifiedDesktopCoverDoesNotCallMobile() = runBlocking {
        val desktop = """{"ok":1,"data":{"user":{"idstr":"42","cover_image":"$cover"}}}"""
        val selected = selectCurrentProfileCandidate("42", config, { desktop }, { throw AssertionError("unexpected mobile call") })
        assertEquals(ProfileCoverState.Available, selected.coverState)
        assertEquals(listOf(cover), selected.profile.coverUrls)
    }

    @Test fun logicalDesktopErrorUsesVerifiedMobileProfile() = runBlocking {
        val selected = selectCurrentProfileCandidate("42", config, { rejectedDesktop }, { mobileCover })
        assertEquals("42", selected.profile.id)
        assertEquals("Mobile", selected.profile.screenName)
        assertEquals(listOf(cover), selected.profile.coverUrls)
    }

    @Test fun coverlessDesktopTakesOnlyCoverFromMatchingMobile() = runBlocking {
        val selected = selectCurrentProfileCandidate("42", config, { desktopOmission }, { mobileCover })
        assertEquals("Desktop", selected.profile.screenName)
        assertEquals(listOf(cover), selected.profile.coverUrls)
    }

    @Test fun missingOrMismatchedMobileIdentityOrLogicalErrorCannotSupplyCover() = runBlocking {
        val invalid = listOf(
            mobileCover.replace("\"id\":42,", ""),
            mobileCover.replace("\"id\":42", "\"id\":43"),
            mobileCover.replace("\"id\":42", "\"id\":42,\"idstr\":\"43\""),
            mobileCover.replace("\"ok\":1", "\"ok\":0"),
            """{"ok":1,"data":{"userInfo":false,"idstr":"42","cover_image":"$cover"}}""",
        )
        invalid.forEach { raw ->
            val selected = selectCurrentProfileCandidate("42", config, { desktopOmission }, { raw })
            assertEquals(ProfileCoverState.Omitted, selected.coverState)
            assertTrue(selected.profile.coverUrls.isEmpty())
        }
    }

    @Test(expected = IllegalStateException::class)
    fun configUidDoesNotValidateEitherResponse() {
        runBlocking {
            val noUid = mobileCover.replace("\"id\":42,", "")
            selectCurrentProfileCandidate("42", config, { rejectedDesktop }, { noUid })
        }
    }

    @Test fun omissionRetainsOnlyUsableCacheFromRequestedAccount() = runBlocking {
        val selected = selectCurrentProfileCandidate("42", config, { desktopOmission }, { rejectedDesktop })
        val cached = WeiboJsonParser.parseUserProfile(mobileCover).copy(
            coverUrls = listOf("https://external.example/size/cover.jpg", cover),
        )
        assertEquals(listOf(cover), selected.retainOmittedCover("42", cached).coverUrls)
        assertTrue(selected.retainOmittedCover(null, cached).coverUrls.isEmpty())
        assertTrue(selected.retainOmittedCover("42", cached.copy(id = "43")).coverUrls.isEmpty())
        assertTrue(selected.retainOmittedCover("43", cached.copy(id = "43")).coverUrls.isEmpty())
    }

    @Test fun explicitClearDoesNotUseMobileOrRetainOldCover() = runBlocking {
        val cached = WeiboJsonParser.parseUserProfile(mobileCover)
        val selected = selectCurrentProfileCandidate("42", config, { explicitClear }, { throw AssertionError("unexpected mobile call") })
        assertEquals(ProfileCoverState.Cleared, selected.coverState)
        assertTrue(selected.retainOmittedCover("42", cached).coverUrls.isEmpty())
        val mobileClear = selectCurrentProfileCandidate("42", config, { desktopOmission }, { explicitClear })
        assertEquals(ProfileCoverState.Cleared, mobileClear.coverState)
        assertTrue(mobileClear.retainOmittedCover("42", cached).coverUrls.isEmpty())
    }

    @Test fun malformedCoverCanUseVerifiedMobileCandidate() = runBlocking {
        val malformed = """{"ok":1,"data":{"user":{"idstr":"42","cover_image":"invalid"}}}"""
        val selected = selectCurrentProfileCandidate("42", config, { malformed }, { mobileCover })
        assertEquals(listOf(cover), selected.profile.coverUrls)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotAReasonToCallMobile() {
        runBlocking {
            selectCurrentProfileCandidate("42", config, { throw CancellationException() }, { throw AssertionError("unexpected mobile call") })
        }
    }
}
