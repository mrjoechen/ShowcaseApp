package com.alpha.facedetection

import com.alpha.facedetection.internal.loadOpenCv
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.opencv.core.Core
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class DesktopFaceInspectorTest {
    @Test
    fun loadsTrimmedOpenCv412Runtime() {
        loadOpenCv()
        assertEquals("4.12.0", Core.VERSION)

        val buildInformation = Core.getBuildInformation()
        val modulesLine = assertNotNull(
            buildInformation.lineSequence().map { it.trim() }.singleOrNull { it.startsWith("To be built:") },
            "Expected exactly one 'To be built:' entry in native OpenCV build information:\n$buildInformation",
        )
        val modules = modulesLine.substringAfter(':').trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }.toSet()
        assertEquals(
            setOf("core", "imgproc", "imgcodecs", "dnn", "objdetect", "java", "calib3d", "features2d", "flann"),
            modules,
            "Loaded native OpenCV must contain exactly the trimmed module set",
        )
    }

    @Test
    fun detectsRealFaceAndRejectsBlankAcrossChangingSizes() = runBlocking {
        val inspector = createFaceInspector()
        assertEquals(FaceInspectionResult.FACE_DETECTED, inspector.inspect(lena()))
        for ((width, height) in listOf(320 to 320, 1280 to 720, 100 to 900)) {
            assertEquals(FaceInspectionResult.NO_FACE, inspector.inspect(blank(width, height)))
        }
        assertEquals(FaceInspectionResult.FACE_DETECTED, inspector.inspect(lena()))
    }

    @Test
    fun concurrentInspectionsDoNotContaminateEachOther() = runBlocking {
        val inspector = createFaceInspector()
        val face = lena()
        val blank = blank(800, 400)
        (0 until 12).map { index ->
            async(Dispatchers.Default) {
                val positive = index % 2 == 0
                assertEquals(
                    if (positive) FaceInspectionResult.FACE_DETECTED else FaceInspectionResult.NO_FACE,
                    inspector.inspect(if (positive) face else blank),
                )
            }
        }.awaitAll()
        Unit
    }

    @Test
    fun decodeFailureDoesNotPoisonLaterInspection() = runBlocking {
        val inspector = createFaceInspector()
        assertEquals(FaceInspectionResult.INDETERMINATE, inspector.inspect(byteArrayOf(1, 2, 3)))
        assertEquals(FaceInspectionResult.FACE_DETECTED, inspector.inspect(lena()))
    }

    @Test
    fun cancellationPropagatesAndInspectorRemainsUsable() = runBlocking {
        val inspector = createFaceInspector()
        val cancelled = launch {
            coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            assertFailsWith<CancellationException> { inspector.inspect(lena()) }
        }
        cancelled.join()
        assertEquals(FaceInspectionResult.FACE_DETECTED, inspector.inspect(lena()))
    }

    private fun lena(): ByteArray = checkNotNull(javaClass.getResourceAsStream("/opencv/lena.jpg"))
        .use { it.readBytes() }

    private fun blank(width: Int, height: Int): ByteArray = ByteArrayOutputStream().use { output ->
        check(ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", output))
        output.toByteArray()
    }
}
