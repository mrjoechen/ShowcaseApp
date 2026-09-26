package com.alpha.showcase.common.ai

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.alpha.showcase.api.unsplash.UnsplashApi
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.repo.UnsplashRepo
import com.alpha.showcase.common.repo.UnSplashSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import com.alpha.showcase.api.unsplash.Photo
import okio.Buffer
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class UnsplashArchiveProbeTest {
    @Test fun actualAndroidArchiveReusesUnsplashSummary() = runBlocking {
        val directory = Files.createTempDirectory("unsplash-probe-").toFile()
        val db = Room.databaseBuilder<ImageSummaryDatabase>(name = File(directory, "summary.db").path)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        try {
            val dao = db.summaries()
            val bytes = File("/Users/joechen/Downloads/showcase-summaries1.scsummary").readBytes()
            assertEquals(SummaryImportResult(145, 0, 0), dao.importArchive(Buffer().write(bytes)))
            val records = mutableListOf<SummaryRevision>()
            val refs = mutableListOf<SummaryFileReference>()
            SummaryArchive.read(Buffer().write(bytes), { records += it }, {}, { refs += it })
            for (record in records) assertEquals(record.content(), dao.find(ImageContentIdentity(record.contentId, record.byteCount!!)))
            val metadata = File("/private/tmp/showcase-unsplash-api-photos.json")
            val photos = if (metadata.exists()) summaryJson.decodeFromString(ListSerializer(Photo.serializer()), metadata.readText()) else {
                UnsplashApi().getUserPhotos("chenqiao", perPage = 100).also {
                    metadata.writeText(summaryJson.encodeToString(ListSerializer(Photo.serializer()), it))
                }
            }
            val photo = photos.first { photo -> refs.any { it.filePath == photo.urls.regular?.substringBefore('?') } }
            val reference = refs.first { it.filePath == photo.urls.regular!!.substringBefore('?') }
            val record = records.first { it.contentId == reference.contentId }
            val repo = UnsplashRepo(pageLoader = { _, _, _ -> listOf(photo) }, maxPages = 1)
            val url = repo.getItems(UnSplashSource("Sample", UnSplashSourceType.UsersPhotos.type, "chenqiao")).getOrThrow().single().data as String
            for ((name, address) in listOf("regular" to photo.urls.regular!!, "selected" to url)) {
                val rendition = if (address == photo.urls.regular) "regular" else "selected"
                val file = File("/private/tmp/showcase-unsplash-probe-$rendition.jpg")
                if (!file.exists()) file.writeBytes(java.net.URI(address).toURL().readBytes())
                val identity = hashImageFile(Buffer().write(file.readBytes()))
                println("$name: $identity; expected ${record.contentId} / ${record.byteCount}; matches=${dao.find(identity) != null}")
                if (name == "selected") assertEquals(record.content(), dao.find(identity), "Same Unsplash photo must reuse the Android summary")
            }
        } finally { db.close(); directory.deleteRecursively() }
    }
}
