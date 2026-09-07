package com.alpha.facedetection

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidFaceInspectorTest {
    @Test
    fun contextFreeFactoryDetectsFaceAndBlankWithTrimmedRuntime() = runBlocking {
        val inspector = createFaceInspector()
        val face = InstrumentationRegistry.getInstrumentation().context.assets.open("opencv/lena.jpg")
            .use { it.readBytes() }
        assertEquals(FaceInspectionResult.FACE_DETECTED, inspector.inspect(face))
        for ((width, height) in listOf(320 to 320, 1280 to 720, 100 to 900)) {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val blank = try {
                ByteArrayOutputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                    it.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
            assertEquals(FaceInspectionResult.NO_FACE, inspector.inspect(blank))
        }
        assertEquals(FaceInspectionResult.INDETERMINATE, inspector.inspect(byteArrayOf(1, 2, 3)))
        assertEquals(FaceInspectionResult.FACE_DETECTED, inspector.inspect(face))
    }
}
