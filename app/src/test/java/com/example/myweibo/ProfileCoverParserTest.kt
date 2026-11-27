package com.example.myweibo

import com.example.myweibo.data.ImageUrlResolver
import com.example.myweibo.data.WeiboJsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileCoverParserTest {
    @Test fun keepsSignedOriginalIncludingCropPathQueryAndQuerySemicolon() {
        val signed = "https://wx2.sinaimg.cn/crop.1.2.3.4/cover.jpg?sig=synthetic%2B%2F%3D;v=1&x=2&x=3"
        val profile = WeiboJsonParser.parseUserProfile(
            """{"data":{"user":{"idstr":"42","cover_image_phone":"$signed"}}}""",
        )
        assertEquals(listOf(signed), profile.coverUrls)
        val image = WeiboJsonParser.profileCoverImages(profile.coverUrls).single()
        assertEquals(signed, image.thumbnailUrl)
        assertEquals(signed, image.largeUrl)
        assertEquals(listOf(signed), image.downloadUrls)
        assertEquals(listOf(signed), ImageUrlResolver.fullscreenCandidates(image))
        assertEquals(listOf(signed), ImageUrlResolver.albumGridCandidates(image))
        assertEquals(listOf(signed), ImageUrlResolver.saveCandidates(image))
    }

    @Test fun keepsFieldOrderDedupesAndOnlyOffersKnownUnsignedRenditions() {
        val crop = "https://wx1.sinaimg.cn/crop.1.2.3.4/phone.jpg"
        val unknownPath = "https://wx4.sinaimg.cn/custom/cover.webp"
        val profile = WeiboJsonParser.parseUserProfile(
            """{"data":{"userInfo":{"id":42,"cover_image_phone":"//wx1.sinaimg.cn/crop.1.2.3.4/phone.jpg; $unknownPath;","cover_image":"$crop"}}}""",
        )
        assertEquals(listOf(crop, unknownPath), profile.coverUrls)
        val (first, second) = WeiboJsonParser.profileCoverImages(profile.coverUrls)
        assertEquals(
            listOf(crop, "https://wx1.sinaimg.cn/mw2000/phone.jpg", "https://wx1.sinaimg.cn/large/phone.jpg"),
            first.downloadUrls,
        )
        assertEquals(listOf(unknownPath), second.downloadUrls)
    }

    @Test fun dropsMalformedOrUntrustedUrlsFromPayloadAndLegacyCache() {
        val rejected = listOf(
            "",
            "not an image",
            "https://external.example/size/cover.jpg",
            "https://wx1.sinaimg.cn.external.example/size/cover.jpg",
            "https://wx1.sinaimg.cn@external.example/size/cover.jpg",
            "https://external.example@wx1.sinaimg.cn/size/cover.jpg",
            "https://wx1.sinaimg.cn:444/size/cover.jpg",
            "https://h5.sinaimg.cn/size/cover.jpg",
            "ftp://wx1.sinaimg.cn/size/cover.jpg",
            "https://wx1.sinaimg.cn/invalid?image=cover.jpg",
        )
        assertTrue(WeiboJsonParser.profileCoverImages(rejected).isEmpty())
        val profile = WeiboJsonParser.parseUserProfile(
            """{"data":{"user":{"idstr":"42","cover_image_phone":"https://external.example/size/cover.jpg","cover_image":"https://wx3.sinaimg.cn/large/approved.png"}}}""",
        )
        assertEquals(listOf("https://wx3.sinaimg.cn/large/approved.png"), profile.coverUrls)
    }
}
