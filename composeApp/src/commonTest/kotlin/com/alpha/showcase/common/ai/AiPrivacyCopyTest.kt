package com.alpha.showcase.common.ai

import com.alpha.showcase.common.ui.ai.aiFaceDetectionUnavailableMessage
import com.alpha.showcase.common.ui.ai.aiFacePrivacyDescription
import com.alpha.showcase.common.ui.ai.aiUploadConsentMessage
import showcaseapp.composeapp.generated.resources.*
import kotlin.test.*

class AiPrivacyCopyTest {
    @Test fun localFaceDetectionKeepsTheExistingCopy() {
        assertSame(Res.string.ai_upload_consent_message, aiUploadConsentMessage(localFaceDetection = true))
        assertSame(Res.string.ai_image_summary_face_privacy_description, aiFacePrivacyDescription(localFaceDetection = true))
        assertSame(Res.string.ai_image_summary_face_detection_failed, aiFaceDetectionUnavailableMessage(localFaceDetection = true))
    }

    @Test fun noLocalFaceDetectionUsesTheAppleTvCopy() {
        assertSame(Res.string.ai_upload_consent_message_tvos, aiUploadConsentMessage(localFaceDetection = false))
        assertSame(Res.string.ai_image_summary_face_privacy_description_tvos, aiFacePrivacyDescription(localFaceDetection = false))
        assertSame(Res.string.ai_image_summary_face_detection_failed_tvos, aiFaceDetectionUnavailableMessage(localFaceDetection = false))
    }
}
