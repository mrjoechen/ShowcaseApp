package com.alpha.showcase.common.ai

import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import kotlin.test.*

class SummaryArchiveValidationTest {
    private val identity = testImageIdentity()
    private val revision = SummaryRevision("revision-1", identity.contentId, identity.byteCount,
        summary = "Photo", narration = "", tags = emptyList(), outputLanguageTag = "en", generatedAtEpochMillis = 1)

    @Test fun rejectsDuplicateFieldsWrongTypesMissingFieldsAndUnknownFields() = runTest {
        val json = summaryJson.encodeToString(revision).replace(",\"diagnostic\":null", "")
        val malformed = listOf(
            json.replace("\"revisionId\":", "\"revisionId\":\"duplicate\",\"revisionId\":"),
            json.replace("\"inputPreparationVersion\":1", "\"inputPreparationVersion\":\"1\""),
            json.replace("\"purpose\":\"slide-summary\",", ""),
            json.replace("\"sourceKind\":\"generated\"", "\"sourceKind\":\"generated\",\"secret\":\"x\""),
        )
        for (body in malformed) assertFails { SummaryArchive.read(archive(listOf(2 to body)), {}, {}, {}) }
    }

    @Test fun rejectsDanglingSelectionsUnsortedRecordsAndUrlCredentials() = runTest {
        val body = summaryJson.encodeToString(revision).replace(",\"diagnostic\":null", "")
        val dangling = SummarySelection(identity.contentId, revisionId = "missing")
        assertFails { SummaryArchive.read(archive(listOf(2 to body, 3 to summaryJson.encodeToString(dangling))), {}, {}, {}) }
        assertFails { SummaryArchive.read(archive(listOf(2 to body, 2 to body)), {}, {}, {}) }
        assertFails { SummaryArchive.validate(SummaryFileReference(identity.contentId, "a".repeat(64), "NAS", "http", "https://user:password@host/photo?token=secret", "photo")) }
        val reference = summaryFileReference("https://user:password@host/photo.jpg?token=secret#fragment", identity)!!
        assertEquals("https://host/photo.jpg", reference.filePath)
        assertFalse(summaryJson.encodeToString(reference).contains("secret"))
    }

    @Test fun currentAndHistoryKeepUnknownByteCountInPortableOutput() = runTest {
        val buffer = Buffer()
        SummaryArchive.Writer(buffer).apply { revision(revision.copy(byteCount = null)); finish() }
        var restored: SummaryRevision? = null
        SummaryArchive.read(buffer, { restored = it }, {}, {})
        assertNotNull(restored)
        assertNull(restored.byteCount)
    }

    /** Independently assembled container so malformed frames still have a valid checksum. */
    private fun archive(frames: List<Pair<Int, String>>): Buffer {
        val output = Buffer().writeUtf8("SCSUMAR1").writeInt(2)
        val manifest = summaryJson.encodeToString(SummaryManifest())
        (listOf(1 to manifest) + frames).forEach { (kind, json) ->
            val bytes = json.encodeUtf8()
            output.writeByte(kind).writeInt(bytes.size).write(bytes)
        }
        val covered = output.size
        val hash = output.snapshot().sha256()
        output.writeByte(255).writeInt(64).writeLong(frames.count { it.first == 2 }.toLong())
            .writeLong(frames.count { it.first == 3 }.toLong()).writeLong(frames.count { it.first == 4 }.toLong())
            .writeLong(covered).write(hash)
        return output
    }
}
