package com.alpha.showcase.common.ai

import com.alpha.facedetection.FaceInspectionResult
import com.alpha.facedetection.createFaceInspector
import com.alpha.showcase.common.ui.ai.aiFaceDetectionUnavailableMessage
import com.alpha.showcase.common.ui.ai.aiFacePrivacyDescription
import com.alpha.showcase.common.ui.ai.aiUploadConsentMessage
import getPlatform
import kotlinx.coroutines.test.runTest
import showcaseapp.composeapp.generated.resources.*
import kotlin.test.*

class TvosAiPrivacyCopyTest {
    @Test fun appleTvDeclaresNoLocalFaceDetection() = runTest {
        assertEquals(FaceInspectionResult.INDETERMINATE, createFaceInspector().inspect(byteArrayOf(1, 2, 3)))
        assertFalse(getPlatform().supportsLocalFaceDetection)
    }

    @Test fun appleTvShowsItsOwnPrivacyCopy() {
        assertSame(Res.string.ai_upload_consent_message_tvos, aiUploadConsentMessage())
        assertSame(Res.string.ai_image_summary_face_privacy_description_tvos, aiFacePrivacyDescription())
        assertSame(Res.string.ai_image_summary_face_detection_failed_tvos, aiFaceDetectionUnavailableMessage())
    }
}
