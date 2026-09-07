package com.alpha.facedetection

import com.alpha.facedetection.internal.OpenCvFaceInspector

actual fun createFaceInspector(): FaceInspector = OpenCvFaceInspector
