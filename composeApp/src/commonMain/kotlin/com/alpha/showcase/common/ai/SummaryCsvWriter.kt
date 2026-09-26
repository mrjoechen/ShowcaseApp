package com.alpha.showcase.common.ai

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import okio.Buffer
import okio.BufferedSink

/** Same columns and escaping as the standalone Android CSV export. Never changes stored text. */
internal class SummaryCsvWriter(
    private val sink: BufferedSink,
    private val maxBytes: Long = SUMMARY_ARCHIVE_LIMIT,
    private val maxRevisions: Long = 100_000,
    private val maxReferences: Long = 1_000_000,
) {
    private var bytes = 0L
    private var revisions = 0L
    private var previousRevision = ""

    init {
        write(Buffer().writeUtf8("\uFEFF"))
        row(listOf(
            "content_id", "revision_id", "is_current", "byte_count", "purpose", "summary", "narration",
            "tags_json", "output_language_tag", "input_preparation_version", "generated_at_epoch_millis",
            "source_kind", "source_name", "source_protocol", "file_path", "file_name",
        ))
    }

    suspend fun revision(
        value: SummaryRevision,
        isCurrent: Boolean,
        references: suspend (suspend (SummaryFileReference) -> Unit) -> Unit = {},
    ) {
        currentCoroutineContext().ensureActive()
        SummaryArchive.validate(value, diagnostics = false)
        require(++revisions <= maxRevisions && value.revisionId > previousRevision)
        previousRevision = value.revisionId
        val columns = listOf(
            value.contentId, value.revisionId, isCurrent.toString(), value.byteCount?.toString().orEmpty(),
            value.purpose, value.summary, value.narration, summaryJson.encodeToString(value.tags),
            value.outputLanguageTag, value.inputPreparationVersion.toString(), value.generatedAtEpochMillis.toString(),
            value.sourceKind,
        )
        var count = 0L
        var previousReference = ""
        references { reference ->
            currentCoroutineContext().ensureActive()
            SummaryArchive.validate(reference)
            require(reference.contentId == value.contentId && reference.referenceId > previousReference && ++count <= maxReferences)
            previousReference = reference.referenceId
            row(columns + listOf(reference.sourceName, reference.sourceProtocol, reference.filePath, reference.fileName))
        }
        if (count == 0L) row(columns + listOf("", "", "", ""))
    }

    suspend fun finish(): Long {
        currentCoroutineContext().ensureActive()
        sink.flush()
        return revisions
    }

    private fun row(values: List<String>) {
        val row = Buffer()
        values.forEachIndexed { index, raw ->
            if (index > 0) row.writeByte(','.code)
            val quoted = raw.any { it in ",\"\r\n" }
            if (quoted) row.writeByte('"'.code)
            if (needsFormulaProtection(raw)) row.writeByte('\''.code)
            row.writeUtf8(raw.replace("\"", "\"\""))
            if (quoted) row.writeByte('"'.code)
        }
        write(row.writeUtf8("\r\n"))
    }

    private fun write(buffer: Buffer) {
        require(buffer.size <= maxBytes - bytes) { "Summary CSV too large" }
        bytes += buffer.size
        sink.write(buffer, buffer.size)
    }

    private fun needsFormulaProtection(value: String): Boolean {
        var index = 0
        var leadingLineControl = false
        while (index < value.length) {
            val character = value[index]
            if (!character.isWhitespace() && !character.isISOControl()) break
            if (character in "\t\r\n") leadingLineControl = true
            index++
        }
        return leadingLineControl || (index < value.length && value[index] in "=+-@")
    }
}
