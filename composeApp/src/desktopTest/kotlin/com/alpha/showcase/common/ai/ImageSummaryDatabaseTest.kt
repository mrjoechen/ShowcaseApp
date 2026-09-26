package com.alpha.showcase.common.ai

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.alpha.showcase.common.cache.SourceDerivedCache
import com.alpha.showcase.api.unsplash.Photo
import com.alpha.showcase.api.unsplash.PhotoUrls
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.repo.UnsplashRepo
import com.alpha.showcase.common.repo.UnSplashSourceType
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Color
import org.jetbrains.skia.Image
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import kotlinx.coroutines.CancellationException
import java.nio.file.Files
import java.io.File
import kotlin.test.*

class ImageSummaryDatabaseTest {
    private val identity = testImageIdentity()
    private val first = AiSummaryContent("A landscape", "Mountains at sunset", listOf("mountain"))
    private val second = AiSummaryContent("A revised landscape", "A mountain sunset", listOf("sunset"))

    @Test fun unsplashPlaybackReusesImportedAndroidRenditionSummary() = runBlocking {
        // The same picture encoded at two sizes has two whole-file identities.
        fun picture(size: Int): ByteArray = Bitmap().use { bitmap ->
            bitmap.allocN32Pixels(size, size)
            bitmap.erase(Color.BLUE)
            Image.makeFromBitmap(bitmap).use { image -> image.encodeToData()!!.use { it.bytes } }
        }
        val regular = "https://images.unsplash.com/photo-test?w=1080&q=80"
        val full = "https://images.unsplash.com/photo-test?q=80"
        val files = mapOf(regular to picture(32), full to picture(64))
        val androidIdentity = hashImageFile(Buffer().write(files.getValue(regular)))
        assertNotEquals(androidIdentity, hashImageFile(Buffer().write(files.getValue(full))))
        val photo = Photo("test", 64, 64, null, null, null, null, null, null,
            PhotoUrls(raw = null, full = full, regular = regular, small = null, thumb = null))
        val repo = UnsplashRepo(pageLoader = { _, _, _ -> listOf(photo) }, maxPages = 1)
        val source = UnSplashSource("Renamed source", UnSplashSourceType.FeedPhotos.type)
        withDatabase { dao ->
            val revision = SummaryRevision("android-rendition", androidIdentity.contentId, androidIdentity.byteCount,
                summary = first.summary, narration = first.narration, tags = first.tags,
                outputLanguageTag = "en", generatedAtEpochMillis = 1)
            val archive = Buffer()
            SummaryArchive.Writer(archive).apply {
                revision(revision)
                selection(SummarySelection(revision.contentId, revisionId = revision.revisionId))
                finish()
            }
            assertEquals(SummaryImportResult(1, 0, 0), dao.importArchive(archive))
            // Exercise both direct playback and the batch path used to populate source caches.
            val urls = repo.getItems(source).getOrThrow().map { it.data as String }.toMutableList()
            repo.streamItems(source, batchSize = 30) { batch -> urls += batch.map { it.path } }.getOrThrow()
            assertEquals(2, urls.size)
            for (url in urls) {
                val displayedIdentity = hashImageFile(Buffer().write(files.getValue(url)))
                assertEquals(first, dao.find(displayedIdentity), "Playback must reuse the Android summary")
            }
        }
    }

    @Test fun historyAndCurrentSurviveCacheClearAndReopeningDatabase(): Unit = runBlocking {
        val directory = Files.createTempDirectory("summary-db-").toFile()
        val path = File(directory, "summaries.db").path
        var db = database(path)
        try {
            val dao = db.summaries()
            dao.save(identity, first, "en")
            dao.save(identity, second, "zh-CN")
            dao.save(identity, second, "en") // Explicit regeneration has its own version even if text matches.
            SourceDerivedCache.clear()
            db.close()
            db = database(path)
            assertEquals(second, db.summaries().find(identity))
            assertEquals(3, db.summaries().countRevisions())
            assertEquals(1, db.summaries().countSummaries())
            val records = mutableListOf<SummaryRevision>()
            val archive = Buffer()
            db.summaries().exportArchive(archive)
            SummaryArchive.read(archive, { records += it }, {}, {})
            assertEquals(listOf(first, second, second).groupingBy { it }.eachCount(), records.map { it.content() }.groupingBy { it }.eachCount())
            assertFailsWith<IllegalArgumentException> { db.summaries().find(identity.copy(byteCount = identity.byteCount + 1)) }
        } finally { db.close(); directory.deleteRecursively() }
    }

    @Test fun importIsIdempotentAndNeverReplacesLocalSelection() = runBlocking {
        withDatabase { local -> withDatabase { remote ->
            local.save(identity, first, "en")
            remote.save(identity, second, "zh-CN")
            val archive = Buffer().also { remote.exportArchive(it) }.readByteArray()
            assertEquals(SummaryImportResult(1, 0, 0), local.importArchive(Buffer().write(archive)))
            assertEquals(SummaryImportResult(0, 1, 0), local.importArchive(Buffer().write(archive)))
            assertEquals(first, local.find(identity))
            assertEquals(2, local.countRevisions())
            assertEquals(1, local.countSummaries())
        } }
    }

    @Test fun checksumTruncationTrailingBytesAndCancellationCannotCommitPartialImports() = runBlocking {
        withDatabase { local -> withDatabase { remote ->
            remote.save(identity, first, "en")
            val bytes = Buffer().also { remote.exportArchive(it) }.readByteArray()
            val corrupt = bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
            for (bad in listOf(corrupt, bytes.copyOf(bytes.size - 1), bytes + byteArrayOf(0))) {
                assertFails { local.importArchive(Buffer().write(bad)) }
                assertEquals(0, local.countRevisions())
                assertNull(local.find(identity))
            }
            val cancelled = object : ForwardingSource(Buffer().write(bytes)) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    val count = super.read(sink, byteCount)
                    if (count == -1L) throw CancellationException("cancelled before validation finished")
                    return count
                }
            }.buffer()
            assertFailsWith<CancellationException> { cancelled.use { local.importArchive(it) } }
            assertEquals(0, local.countRevisions())
            assertEquals(1, local.importArchive(Buffer().write(bytes)).added)
        } }
    }

    @Test fun oldSummaryAndEveryHistoryVersionMigrateWithoutReplacingAnImportedSelection() = runBlocking {
        withDatabase { dao ->
            dao.save(identity, second, "zh-CN")
            val records = mapOf("legacy-profile-and-path-key" to listOf(first, second))
            dao.migrate(identity, records, "en")
            dao.migrate(identity, records, "en")
            assertEquals(3, dao.countRevisions())
            assertEquals(second, dao.find(identity))
        }
    }

    @Test fun standaloneAndroidV1AndV2FixturesImportAndReexportWithReferencesAndDiagnostics() = runBlocking {
        for (version in 1..2) withDatabase { dao ->
            val bytes = javaClass.getResourceAsStream("/summary-archives/android-v$version.scsummary")!!.use { it.readBytes() }
            assertEquals(SummaryImportResult(2, 0, 0), dao.importArchive(Buffer().write(bytes)))
            val archive = Buffer()
            val counts = dao.exportArchive(archive)
            assertEquals(2, counts.revisions)
            assertEquals(if (version == 2) 1L else 0L, counts.fileReferences)
            val exported = archive.readByteArray()
            // Also checked by the independent Android repository Python reader after Gradle tests.
            File("build/summary-archive-tests").mkdirs()
            File("build/summary-archive-tests/android-v$version-reexport.scsummary").writeBytes(exported)
            val records = mutableListOf<SummaryRevision>()
            val references = mutableListOf<SummaryFileReference>()
            SummaryArchive.read(Buffer().write(exported), { records += it }, {}, { references += it })
            assertEquals(2, records.size)
            assertNotNull(records.first().diagnostic)
            if (version == 2) assertEquals("/照片/假期/海边.jpg", references.single().filePath)
        }
    }

    @Test fun conflictingRevisionIdsAndContentLengthsAreSkippedWithoutLosingHistory() = runBlocking {
        withDatabase { dao ->
            val value = SummaryRevision("same-id", identity.contentId, identity.byteCount, summary = first.summary,
                narration = first.narration, tags = first.tags, outputLanguageTag = "en", generatedAtEpochMillis = 1)
            fun archive(revision: SummaryRevision): Buffer = Buffer().also { sink ->
                SummaryArchive.Writer(sink).apply { revision(revision); selection(SummarySelection(revision.contentId, revisionId = revision.revisionId)); finish() }
            }
            dao.importArchive(archive(value))
            assertEquals(1, dao.importArchive(archive(value.copy(summary = "Conflicting text"))).conflicts)
            assertEquals(1, dao.importArchive(archive(value.copy(revisionId = "new-id", byteCount = identity.byteCount + 1))).conflicts)
            assertEquals(1, dao.countRevisions())
            assertEquals(first, dao.find(identity))
        }
    }

    private fun database(path: String) = Room.databaseBuilder<ImageSummaryDatabase>(name = path)
        .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()

    private suspend fun withDatabase(block: suspend (ImageSummaryDao) -> Unit) {
        val directory = Files.createTempDirectory("summary-test-").toFile()
        val db = database(File(directory, "summaries.db").path)
        try { block(db.summaries()) } finally { db.close(); directory.deleteRecursively() }
    }
}
