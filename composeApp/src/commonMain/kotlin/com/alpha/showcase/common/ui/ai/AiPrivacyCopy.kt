package com.alpha.showcase.common.ui.ai

import getPlatform
import org.jetbrains.compose.resources.StringResource
import showcaseapp.composeapp.generated.resources.*

// Without local face detection (only Apple TV), the copy must not promise that faces are detected on the device.

internal fun aiUploadConsentMessage(localFaceDetection: Boolean = getPlatform().supportsLocalFaceDetection): StringResource =
    if (localFaceDetection) Res.string.ai_upload_consent_message else Res.string.ai_upload_consent_message_tvos

internal fun aiFacePrivacyDescription(localFaceDetection: Boolean = getPlatform().supportsLocalFaceDetection): StringResource =
    if (localFaceDetection) Res.string.ai_image_summary_face_privacy_description
    else Res.string.ai_image_summary_face_privacy_description_tvos

internal fun aiFaceDetectionUnavailableMessage(localFaceDetection: Boolean = getPlatform().supportsLocalFaceDetection): StringResource =
    if (localFaceDetection) Res.string.ai_image_summary_face_detection_failed
    else Res.string.ai_image_summary_face_detection_failed_tvos
