package com.alpha.showcase.common.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okio.Buffer
import kotlin.test.*

class SummaryCsvWriterTest {
    private val contentId = "sha256-file-v1:" + "a".repeat(64)
    private val revision = SummaryRevision("revision-1", contentId, 68, summary = "saved", narration = "narration",
        tags = emptyList(), outputLanguageTag = "en", generatedAtEpochMillis = 8)
    private val reference = SummaryFileReference(contentId, "1".repeat(64), "家中 NAS", "SMB", "/相册/海,山.jpg", "海,山.jpg")
    private val header = "\uFEFFcontent_id,revision_id,is_current,byte_count,purpose,summary,narration,tags_json," +
        "output_language_tag,input_preparation_version,generated_at_epoch_millis,source_kind,source_name,source_protocol,file_path,file_name\r\n"

    @Test fun matchesAndroidChineseQuotesNewlinesBomAndEmptyByteCount(): Unit = runBlocking {
        val value = revision.copy(byteCount = null, summary = "中文,\"引号\"\r\n下一行", narration = "旁白\n尾行",
            tags = listOf("山,海", "中文\"标签"), outputLanguageTag = "zh-CN")
        val buffer = Buffer()
        val writer = SummaryCsvWriter(buffer)
        writer.revision(value, true)
        assertEquals(1, writer.finish())
        assertEquals(header + "$contentId,revision-1,true,,slide-summary,\"中文,\"\"引号\"\"\r\n下一行\",\"旁白\n尾行\"," +
            "\"[\"\"山,海\"\",\"\"中文\\\"\"标签\"\"]\",zh-CN,1,8,generated,,,,\r\n", buffer.readUtf8())
        assertEquals("中文,\"引号\"\r\n下一行", value.summary)
    }

    @Test fun protectsFormulasAndLeadingControlsAcrossEveryTextColumn(): Unit = runBlocking {
        val guarded = listOf("=1+1", "+1", "-1", "@SUM(A1)", "  =1", "\u0000\u00a0=1", "\u0085+1",
            "\tplain", " \rplain", "\nplain", "\u2007\u202f-1")
        val ordinary = listOf("普通文本", "  ordinary", "hello = world", "'already text", " \u0000plain")
        for (value in guarded + ordinary) {
            val buffer = Buffer()
            val writer = SummaryCsvWriter(buffer)
            writer.revision(revision.copy(summary = value), false)
            writer.finish()
            val cell = (if (value in guarded) "'" else "") + value
            val encoded = if (value.any { it in ",\"\r\n" }) "\"$cell\"" else cell
            assertTrue(buffer.readUtf8().contains(",slide-summary,$encoded,narration,[],en,1,8,generated,,,,\r\n"))
        }
    }

    @Test fun expandsFileLocationsAndKeepsUnlocatedHistory(): Unit = runBlocking {
        val buffer = Buffer()
        val writer = SummaryCsvWriter(buffer)
        writer.revision(revision, true) { emit ->
            emit(reference)
            emit(reference.copy(referenceId = "2".repeat(64), sourceName = "=NAS", filePath = "/另一张.jpg", fileName = "@photo.jpg"))
        }
        writer.revision(revision.copy(revisionId = "revision-2", contentId = "sha256-file-v1:" + "b".repeat(64)), false)
        assertEquals(2, writer.finish())
        val rows = buffer.readUtf8().split("\r\n")
        assertEquals(5, rows.size)
        assertTrue(rows[1].endsWith(",家中 NAS,SMB,\"/相册/海,山.jpg\",\"海,山.jpg\""))
        assertTrue(rows[2].endsWith(",'=NAS,SMB,/另一张.jpg,'@photo.jpg"))
        assertTrue(rows[3].contains(",revision-2,false,"))
        assertTrue(rows[3].endsWith(",generated,,,,"))
    }

    @Test fun emptyLibraryHasHeader(): Unit = runBlocking {
        val buffer = Buffer()
        assertEquals(0, SummaryCsvWriter(buffer).finish())
        assertEquals(header, buffer.readUtf8())
    }

    @Test fun boundsActualExpandedBytesAndRevisionCounts(): Unit = runBlocking {
        val single = Buffer()
        SummaryCsvWriter(single).apply { revision(revision, true) { it(reference) }; finish() }
        val bounded = Buffer()
        val writer = SummaryCsvWriter(bounded, maxBytes = single.size)
        assertFailsWith<IllegalArgumentException> {
            writer.revision(revision, true) { emit -> emit(reference); emit(reference.copy(referenceId = "2".repeat(64))) }
        }
        assertEquals(single.size, bounded.size)
        val one = SummaryCsvWriter(Buffer(), maxRevisions = 1)
        one.revision(revision, false)
        assertFailsWith<IllegalArgumentException> { one.revision(revision.copy(revisionId = "revision-2"), false) }
        assertFailsWith<IllegalArgumentException> { SummaryCsvWriter(Buffer(), maxBytes = 2) }
    }

    @Test fun rejectsWrongIdentityDuplicateOrderingAndExcessiveReferences(): Unit = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            SummaryCsvWriter(Buffer()).revision(revision, false) { it(reference.copy(contentId = "sha256-file-v1:" + "b".repeat(64))) }
        }
        assertFailsWith<IllegalArgumentException> {
            SummaryCsvWriter(Buffer()).revision(revision, false) { it(reference); it(reference) }
        }
        assertFailsWith<IllegalArgumentException> {
            SummaryCsvWriter(Buffer(), maxReferences = 1).revision(revision, false) { it(reference); it(reference.copy(referenceId = "2".repeat(64))) }
        }
        val writer = SummaryCsvWriter(Buffer())
        writer.revision(revision, false)
        assertFailsWith<IllegalArgumentException> { writer.revision(revision, false) }
    }

    @Test fun cancellationStopsExpandedRows(): Unit = runBlocking {
        val buffer = Buffer()
        val writer = SummaryCsvWriter(buffer)
        var firstRowBytes = 0L
        assertFailsWith<CancellationException> {
            withContext(Job()) {
                writer.revision(revision, true) { emit ->
                    emit(reference)
                    firstRowBytes = buffer.size
                    currentCoroutineContext().job.cancel()
                    emit(reference.copy(referenceId = "2".repeat(64)))
                }
            }
        }
        assertEquals(firstRowBytes, buffer.size)
    }
}
