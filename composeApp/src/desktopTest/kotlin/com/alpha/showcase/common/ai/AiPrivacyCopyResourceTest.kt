package com.alpha.showcase.common.ai

import com.alpha.showcase.common.ui.ai.aiFaceDetectionUnavailableMessage
import com.alpha.showcase.common.ui.ai.aiFacePrivacyDescription
import com.alpha.showcase.common.ui.ai.aiUploadConsentMessage
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import kotlin.test.*

class AiPrivacyCopyResourceTest {
    @Test fun desktopKeepsTheLocalFaceDetectionCopy() = runTest {
        val english = environmentFor(Locale.US)
        assertContains(getString(english, aiUploadConsentMessage(), "api.example.test"), "faces are detected locally on your device")
        assertContains(getString(english, aiFaceDetectionUnavailableMessage()), "double-click to retry")
    }

    @Test fun appleTvConsentNamesTheHostAndSaysNothingIsSentWhileFacePrivacyIsOn() = runTest {
        val english = getString(environmentFor(Locale.US), aiUploadConsentMessage(false), "api.example.test")
        assertContains(english, "(api.example.test)")
        assertContains(english, "Apple TV cannot detect faces")
        assertContains(english, "no photos are sent")
        assertFalse("detected locally" in english)
        val chinese = getString(environmentFor(Locale.SIMPLIFIED_CHINESE), aiUploadConsentMessage(false), "api.example.test")
        assertContains(chinese, "（api.example.test）")
        assertContains(chinese, "Apple TV 无法在设备本地检测人脸")
        assertContains(chinese, "不会发送任何照片")
        assertFalse("自动跳过" in chinese)
    }

    @Test fun appleTvFacePrivacyCopyIsTranslatedAndOffersNoRetry() = runTest {
        for (locale in listOf(Locale.US, Locale.SIMPLIFIED_CHINESE)) {
            val environment = environmentFor(locale)
            val description = getString(environment, aiFacePrivacyDescription(false))
            val blocked = getString(environment, aiFaceDetectionUnavailableMessage(false))
            assertContains(description, "Apple TV")
            assertContains(blocked, "Apple TV")
            assertNotEquals(getString(environment, aiFacePrivacyDescription(true)), description)
            for (retry in listOf("double-click", "retry", "双击", "重试")) assertFalse(retry in blocked, "$locale: $blocked")
        }
        assertContains(getString(environmentFor(Locale.SIMPLIFIED_CHINESE), aiFaceDetectionUnavailableMessage(false)), "人脸隐私保护")
    }

    private fun environmentFor(locale: Locale): ResourceEnvironment = synchronized(Locale::class.java) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            getSystemResourceEnvironment()
        } finally {
            Locale.setDefault(original)
        }
    }
}
