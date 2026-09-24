package com.alpha.showcase.common.ai

import com.alpha.showcase.common.ui.settings.Settings
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FADE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CALENDER
import com.alpha.showcase.common.ui.ai.isAiSummaryEnabled
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.ui.play.ResolvedImageModel
import com.alpha.showcase.common.repo.SignedS3ObjectUrl
import kotlinx.serialization.json.*
import kotlin.test.*

class AiSummaryTest {
    @Test fun cacheScopeIncludesLocaleAndMediaIdentity() {
        assertNotEquals(aiSummaryKey("image.jpg", "zh-CN"), aiSummaryKey("image.jpg", "en-US"))
        assertNotEquals(aiSummaryKey("image.jpg", "en-US"), aiSummaryKey("other.jpg", "en-US"))
    }

    @Test fun signedImagesUseTheirCacheIdentityInsteadOfRedactedDebugText() {
        fun image(path: String, signature: String, version: String = "1") = DataWithType(
            ResolvedImageModel(SignedS3ObjectUrl("https://photos.example/$path?signature=$signature", 123456789L),
                stableKey = path, cacheKey = "$path:$version", refreshSignedRequest = { error("No fetch expected") }), "image/jpeg")
        val first = image("first.jpg", "old")
        val second = image("second.jpg", "old")
        assertEquals(first.data.toString(), second.data.toString()) // Both deliberately redact the image identity.
        assertNotEquals(aiSummaryKey(first, "en-US"), aiSummaryKey(second, "en-US"))
        assertEquals(aiSummaryKey(first, "en-US"), aiSummaryKey(image("first.jpg", "renewed"), "en-US"))
        assertNotEquals(aiSummaryKey(first, "en-US"), aiSummaryKey(image("first.jpg", "old", "2"), "en-US"))
    }

    @Test fun parsingKeepsOriginalNarrationAndTagRules() {
        val result = parseAiSummary(buildJsonObject {
            put("summary", "晴朗的海边")
            put("narration", "“ 海风\n  还没下班 ”")
            put("tags", buildJsonArray { add("#海边"); add("海边"); add("  风景  "); add(123) })
        }, "zh-CN")!!
        assertEquals("海风 还没下班", result.narration.trim())
        assertEquals(listOf("海边", "风景"), result.tags)
        assertNull(parseAiSummary(buildJsonObject { put("summary", 12) }, "en-US"))
        assertNull(parseAiSummary(buildJsonObject { put("summary", "A photo"); put("narration", "A moment"); put("tags", JsonArray(emptyList())) }, "en-US"))
    }

    @Test fun summarySettingsAreIndependentAndDefaultOff() {
        val settings = Settings(slideMode = Settings.SlideMode(enableAiImageSummary = true), showcaseMode = SHOWCASE_MODE_SLIDE)
        assertTrue(settings.isAiSummaryEnabled())
        assertFalse(settings.copy(showcaseMode = SHOWCASE_MODE_FADE).isAiSummaryEnabled())
        assertFalse(settings.copy(showcaseMode = SHOWCASE_MODE_CALENDER).isAiSummaryEnabled())
        assertFalse(Settings().isAiSummaryEnabled())
    }
}
