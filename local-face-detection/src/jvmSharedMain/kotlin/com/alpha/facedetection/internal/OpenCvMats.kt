package com.alpha.facedetection.internal

import org.opencv.core.Mat

internal inline fun <M : Mat, T> M.useMat(block: (M) -> T): T = try {
    block(this)
} finally {
    release()
}
