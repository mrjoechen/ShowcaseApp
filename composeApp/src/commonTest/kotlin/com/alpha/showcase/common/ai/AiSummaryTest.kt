package com.alpha.showcase.common.ai

import com.alpha.showcase.common.ui.settings.Settings
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_SLIDE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_FADE
import com.alpha.showcase.common.ui.settings.SHOWCASE_MODE_CALENDER
import com.alpha.showcase.common.ui.ai.isAiSummaryEnabled
import kotlinx.serialization.json.*
import kotlin.test.*

class AiSummaryTest {
    private val profile = AiProfile("p", name = "Test", providerId = "openai-vision", model = "vision", baseUrl = "https://api.example", encryptedToken = "")

    @Test fun cacheScopeIncludesLocaleAndProfileRevision() {
        assertNotEquals(aiSummaryKey("image.jpg", profile, "zh-CN"), aiSummaryKey("image.jpg", profile, "en-US"))
        assertNotEquals(aiSummaryKey("image.jpg", profile, "en-US"), aiSummaryKey("image.jpg", profile.copy(revision = 2), "en-US"))
        assertNotEquals(aiSummaryKey("image.jpg", profile, "en-US"), aiSummaryKey("other.jpg", profile, "en-US"))
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
