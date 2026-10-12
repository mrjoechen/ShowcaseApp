package com.alpha.facedetection

/** The pinned OpenCV distribution has no tvOS slice; fail closed for face-sensitive workflows. */
actual fun createFaceInspector(): FaceInspector = FaceInspector { FaceInspectionResult.INDETERMINATE }
