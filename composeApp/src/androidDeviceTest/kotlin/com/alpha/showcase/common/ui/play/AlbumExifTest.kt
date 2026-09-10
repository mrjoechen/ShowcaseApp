package com.alpha.showcase.common.ui.play

import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.request.SuccessResult
import com.alpha.showcase.common.addPlatformComponents
import com.alpha.showcase.common.ui.ext.buildMediaImageRequest
import de.stefan_oltmann.kim.format.jpeg.JpegRewriter
import de.stefan_oltmann.kim.format.tiff.constant.TiffTag
import de.stefan_oltmann.kim.format.tiff.constant.ExifTag
import de.stefan_oltmann.kim.format.tiff.write.TiffOutputSet
import de.stefan_oltmann.kim.input.ByteArrayByteReader
import de.stefan_oltmann.kim.model.GpsCoordinates
import de.stefan_oltmann.kim.output.ByteArrayByteWriter
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class AlbumExifTest {
    @SdkSuppress(minSdkVersion = 29)
    @Test fun albumContentUriRetainsExifAndPixelsAcrossCacheHits() = runTest {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
        val jpeg = ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
            bitmap.recycle()
            it.toByteArray()
        }
        val writer = ByteArrayByteWriter()
        JpegRewriter.updateExifMetadata(ByteArrayByteReader(jpeg), writer, TiffOutputSet().apply {
            getOrCreateRootDirectory().apply {
                add(TiffTag.TIFF_TAG_MODEL, "Album EXIF Camera")
                // Camera metadata/embedded previews can span multiple Okio segments.
                add(TiffTag.TIFF_TAG_COPYRIGHT, "Fixture copyright ".repeat(1000))
            }
            getOrCreateExifDirectory().add(ExifTag.EXIF_TAG_LENS_MODEL, "Album EXIF Lens")
            setGpsCoordinates(GpsCoordinates(31.2304, 121.4737))
        })
        val resolver = context.contentResolver
        val uri = assertNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "showcase-exif-regression.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }))
        val loader = ImageLoader.Builder(context).mediaMetadataCache(
            MemoryCache.Builder().maxSizeBytes(8 * 1024 * 1024).build()
        ) { addPlatformComponents() }.build()
        val localFile = java.io.File.createTempFile("showcase-exif-", ".jpg", context.cacheDir)
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.ACCESS_MEDIA_LOCATION)
        try {
            assertNotNull(resolver.openOutputStream(uri)).use { it.write(writer.toByteArray()) }
            localFile.writeBytes(writer.toByteArray())
            // Exercise Android's content resolver and filesystem fetchers separately.
            for (path in listOf(uri.toString(), localFile.absolutePath)) {
                val request = buildMediaImageRequest(context, path)
                repeat(2) { index ->
                    val result = assertIs<SuccessResult>(loader.execute(request))
                    assertEquals(800, result.image.width)
                    assertEquals(400, result.image.height)
                    assertContains(assertNotNull(result.mediaMetadata).lines.joinToString("\n"), "Album EXIF Camera")
                    assertContains(assertNotNull(result.mediaMetadata).lines.joinToString("\n"), "Album EXIF Lens")
                    val coordinates = assertNotNull(result.mediaMetadata?.coordinates)
                    assertEquals(31.2304, coordinates.latitude, 0.000001)
                    assertEquals(121.4737, coordinates.longitude, 0.000001)
                    if (index == 1) assertEquals(coil3.decode.DataSource.MEMORY_CACHE, result.dataSource)
                }
            }
        } finally {
            loader.shutdown()
            resolver.delete(uri, null, null)
            localFile.delete()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }
}
