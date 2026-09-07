package com.alpha.facedetection

import com.alpha.facedetection.internal.UnsupportedFaceInspector

actual fun createFaceInspector(): FaceInspector = UnsupportedFaceInspector
