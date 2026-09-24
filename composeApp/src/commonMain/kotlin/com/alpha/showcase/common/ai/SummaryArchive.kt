package com.alpha.showcase.common.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okio.BufferedSink
import okio.BufferedSource
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8
import okio.HashingSink
import okio.blackholeSink
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal const val SUMMARY_PURPOSE = "slide-summary"
internal const val SUMMARY_ARCHIVE_LIMIT = 256L * 1024 * 1024
internal val summaryJson = Json { encodeDefaults = true; explicitNulls = true }

@Serializable
internal data class SummaryManifest(
    val formatVersion: Int = 2,
    val packageId: String = Uuid.random().toString(),
    val contentHashVersion: String = "sha256-file-v1",
    val createdAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
    val diagnosticsIncluded: Boolean = false,
)

@Serializable
internal data class SummaryRevision(
    val revisionId: String,
    val contentId: String,
    val byteCount: Long?,
    val purpose: String = SUMMARY_PURPOSE,
    val summary: String,
    val narration: String,
    val tags: List<String>,
    val outputLanguageTag: String,
    val inputPreparationVersion: Int = 1,
    val generatedAtEpochMillis: Long,
    val sourceKind: String = "generated",
    val diagnostic: SummaryDiagnostic? = null,
) {
    fun content() = AiSummaryContent(summary, narration, tags)
}

@Serializable
internal data class SummaryDiagnostic(val bytes: String, val originalLength: Long, val sha256: String, val truncated: Boolean)

@Serializable
internal data class SummarySelection(val contentId: String, val purpose: String = SUMMARY_PURPOSE, val revisionId: String)

@Serializable
internal data class SummaryFileReference(
    val contentId: String, val referenceId: String, val sourceName: String,
    val sourceProtocol: String, val filePath: String, val fileName: String,
)

internal data class SummaryArchiveCounts(val revisions: Long, val selections: Long, val fileReferences: Long)

/** Wire-compatible with the standalone Android SCSUMAR1 v1/v2 codec. Payloads are streamed. */
internal object SummaryArchive {
    private val magic = "SCSUMAR1".encodeUtf8()
    private val idPattern = Regex("[A-Za-z0-9._:-]{1,128}")
    private val hashPattern = Regex("[0-9a-f]{64}")
    private fun contentId(value: String) = require(value.matches(Regex("sha256-file-v1:[0-9a-f]{64}")))
    private fun text(value: String, limit: Int, nonblank: Boolean = true) {
        require((!nonblank || value.isNotBlank()) && value.encodeUtf8().size <= limit)
    }

    fun validate(revision: SummaryRevision, diagnostics: Boolean = true) {
        require(idPattern.matches(revision.revisionId))
        contentId(revision.contentId)
        require(revision.byteCount == null || revision.byteCount >= 0)
        require(revision.purpose == SUMMARY_PURPOSE)
        text(revision.summary, 256 * 1024)
        text(revision.narration, 512 * 1024, false)
        require(revision.tags.size <= 5 && revision.tags.distinct().size == revision.tags.size)
        revision.tags.forEach { text(it, 256); require(it == it.trim()) }
        text(revision.outputLanguageTag, 64)
        require(revision.inputPreparationVersion > 0 && revision.generatedAtEpochMillis >= 0)
        require(revision.sourceKind in setOf("generated", "imported", "unspecified"))
        revision.diagnostic?.let {
            require(diagnostics)
            val bytes = requireNotNull(it.bytes.decodeBase64())
            require(bytes.size <= 256 * 1024 && it.originalLength >= bytes.size && hashPattern.matches(it.sha256))
            require(it.truncated || (it.originalLength == bytes.size.toLong() && bytes.sha256().hex() == it.sha256))
        }
    }

    fun validate(reference: SummaryFileReference) {
        contentId(reference.contentId)
        require(hashPattern.matches(reference.referenceId))
        text(reference.sourceName, 2048, false)
        text(reference.sourceProtocol, 64)
        text(reference.filePath, 8192)
        text(reference.fileName, 2048)
        require(listOf(reference.sourceName, reference.sourceProtocol, reference.filePath, reference.fileName).none { '\u0000' in it })
        if (Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(reference.filePath)) {
            val authority = reference.filePath.substringAfter("://").substringBefore('/')
            require('@' !in authority && '?' !in reference.filePath && '#' !in reference.filePath)
        }
    }

    suspend fun read(
        source: BufferedSource,
        revision: suspend (SummaryRevision) -> Unit,
        selection: suspend (SummarySelection) -> Unit,
        reference: suspend (SummaryFileReference) -> Unit,
    ): SummaryArchiveCounts {
        val digest = HashingSink.sha256(blackholeSink())
        var covered = 0L
        var terminalFrameBytes = 69L
        fun cover(bytes: ByteString) {
            covered += bytes.size
            require(covered + terminalFrameBytes <= SUMMARY_ARCHIVE_LIMIT) { "Summary archive too large" }
            digest.write(Buffer().write(bytes), bytes.size.toLong())
        }
        val header = source.readByteString(12)
        val headerBuffer = Buffer().write(header)
        require(headerBuffer.readByteString(8) == magic) { "Not a summary archive" }
        val version = headerBuffer.readInt()
        require(version in 1..2) { "Unsupported summary archive version" }
        terminalFrameBytes = if (version == 1) 61 else 69
        cover(header)
        var manifest: SummaryManifest? = null
        var group = 1
        var lastRevision = ""
        var lastSelection = ""
        var lastReference = ""
        val identities = mutableMapOf<String, Long?>()
        val revisions = mutableMapOf<String, String>()
        var selectionCount = 0L
        var referenceCount = 0L
        while (true) {
            val kind = source.readByte().toInt() and 255
            val size = source.readInt()
            if (kind == 255) {
                require(manifest != null && size == if (version == 1) 56 else 64)
                require(source.readLong() == revisions.size.toLong())
                require(source.readLong() == selectionCount)
                if (version == 2) require(source.readLong() == referenceCount)
                require(source.readLong() == covered && source.readByteString(32) == digest.hash) { "Summary archive checksum mismatch" }
                require(source.exhausted()) { "Trailing archive data" }
                return SummaryArchiveCounts(revisions.size.toLong(), selectionCount, referenceCount)
            }
            val limit = when (kind) { 1 -> 16 * 1024; 2 -> 1024 * 1024; 3 -> 512; 4 -> 32 * 1024; else -> error("Unknown archive frame") }
            require(size in 1..limit && kind >= group && (version == 2 || kind != 4))
            val bytes = source.readByteString(size.toLong())
            cover(Buffer().writeByte(kind).writeInt(size).readByteString())
            cover(bytes)
            // JSON is strict: reject malformed UTF-8, duplicate/unknown keys and non-finite numbers.
            val json = bytes.utf8()
            require(json.encodeUtf8() == bytes) { "Invalid UTF-8" }
            rejectDuplicateJsonKeys(json)
            requireWireFields(summaryJson.parseToJsonElement(json).jsonObject, kind)
            when (kind) {
                1 -> {
                    require(manifest == null)
                    manifest = summaryJson.decodeFromString<SummaryManifest>(json).also {
                        require(it.formatVersion == version && it.contentHashVersion == "sha256-file-v1")
                        require(Uuid.parse(it.packageId).toString() == it.packageId && it.createdAtEpochMillis >= 0)
                    }
                }
                2 -> {
                    val item = summaryJson.decodeFromString<SummaryRevision>(json)
                    validate(item, requireNotNull(manifest).diagnosticsIncluded)
                    require(item.revisionId > lastRevision && revisions.size < 100_000)
                    lastRevision = item.revisionId
                    val oldSize = identities[item.contentId]
                    require(oldSize == null || item.byteCount == null || oldSize == item.byteCount)
                    identities[item.contentId] = item.byteCount ?: oldSize
                    revisions[item.revisionId] = item.contentId
                    revision(item)
                }
                3 -> {
                    require(manifest != null)
                    val item = summaryJson.decodeFromString<SummarySelection>(json)
                    val order = "${item.contentId}\u0000${item.purpose}"
                    require(item.purpose == SUMMARY_PURPOSE && order > lastSelection && ++selectionCount <= 100_000)
                    require(revisions[item.revisionId] == item.contentId) { "Dangling archive selection" }
                    lastSelection = order
                    selection(item)
                }
                4 -> {
                    require(manifest != null)
                    val item = summaryJson.decodeFromString<SummaryFileReference>(json)
                    validate(item)
                    val order = "${item.contentId}\u0000${item.referenceId}"
                    require(order > lastReference && ++referenceCount <= 1_000_000 && item.contentId in identities)
                    lastReference = order
                    reference(item)
                }
            }
            group = kind
        }
    }

    class Writer(private val sink: BufferedSink, diagnosticsIncluded: Boolean = true) {
        private val digest = HashingSink.sha256(blackholeSink())
        private var covered = 0L
        private var revisions = 0L
        private var selections = 0L
        private var references = 0L
        init {
            write(Buffer().write(magic).writeInt(2).readByteString())
            frame(1, summaryJson.encodeToString(SummaryManifest(diagnosticsIncluded = diagnosticsIncluded)))
        }
        private fun write(bytes: ByteString) {
            covered += bytes.size
            require(covered + 69 <= SUMMARY_ARCHIVE_LIMIT) { "Summary archive too large" }
            digest.write(Buffer().write(bytes), bytes.size.toLong())
            sink.write(bytes)
        }
        private fun frame(kind: Int, json: String) {
            val data = json.encodeUtf8()
            require(data.size <= when (kind) { 1 -> 16 * 1024; 2 -> 1024 * 1024; 3 -> 512; else -> 32 * 1024 })
            write(Buffer().writeByte(kind).writeInt(data.size).write(data).readByteString())
        }
        fun revision(value: SummaryRevision) {
            validate(value); require(++revisions <= 100_000)
            // Android permits absent diagnostics; its reader does not accept explicit null.
            val body = summaryJson.encodeToJsonElement(SummaryRevision.serializer(), value) as kotlinx.serialization.json.JsonObject
            frame(2, kotlinx.serialization.json.JsonObject(body.filter { (key, value) -> key != "diagnostic" || value != kotlinx.serialization.json.JsonNull }).toString())
        }
        fun selection(value: SummarySelection) { require(++selections <= 100_000); frame(3, summaryJson.encodeToString(value)) }
        fun reference(value: SummaryFileReference) { validate(value); require(++references <= 1_000_000); frame(4, summaryJson.encodeToString(value)) }
        fun finish(): SummaryArchiveCounts {
            sink.writeByte(255).writeInt(64).writeLong(revisions).writeLong(selections).writeLong(references).writeLong(covered).write(digest.hash)
            sink.flush()
            return SummaryArchiveCounts(revisions, selections, references)
        }
    }
}

private fun requireWireFields(value: JsonObject, kind: Int) {
    val strings: Set<String>
    val numbers: Set<String>
    val booleans: Set<String>
    when (kind) {
        1 -> { strings = setOf("packageId", "contentHashVersion"); numbers = setOf("formatVersion", "createdAtEpochMillis"); booleans = setOf("diagnosticsIncluded") }
        2 -> { strings = setOf("revisionId", "contentId", "purpose", "summary", "narration", "outputLanguageTag", "sourceKind"); numbers = setOf("inputPreparationVersion", "generatedAtEpochMillis"); booleans = emptySet() }
        3 -> { strings = setOf("contentId", "purpose", "revisionId"); numbers = emptySet(); booleans = emptySet() }
        4 -> { strings = setOf("contentId", "referenceId", "sourceName", "sourceProtocol", "filePath", "fileName"); numbers = emptySet(); booleans = emptySet() }
        else -> { strings = setOf("bytes", "sha256"); numbers = setOf("originalLength"); booleans = setOf("truncated") }
    }
    val required = strings + numbers + booleans + if (kind == 2) setOf("tags", "byteCount") else emptySet()
    require(value.keys.containsAll(required) && value.keys.all { it in required || (kind == 2 && it == "diagnostic") })
    strings.forEach { require((value[it] as? JsonPrimitive)?.isString == true) }
    numbers.forEach { require((value[it] as? JsonPrimitive)?.let { number -> !number.isString && number.longOrNull != null } == true) }
    booleans.forEach { require((value[it] as? JsonPrimitive)?.let { flag -> !flag.isString && flag.booleanOrNull != null } == true) }
    if (kind == 2) {
        val bytes = value.getValue("byteCount")
        require(bytes == JsonNull || (bytes is JsonPrimitive && !bytes.isString && bytes.longOrNull != null))
        require(value["tags"] is JsonArray && value["tags"]!!.jsonArray.all { it is JsonPrimitive && it.isString })
        value["diagnostic"]?.let { requireWireFields(it.jsonObject, 5) }
    }
}

/** kotlinx.serialization otherwise accepts duplicate object keys. Validate before deserializing. */
private fun rejectDuplicateJsonKeys(json: String) {
    var offset = 0
    fun space() { while (offset < json.length && json[offset].isWhitespace()) offset++ }
    fun string(): String {
        val start = offset++
        while (offset < json.length) {
            when (json[offset++]) {
                '\\' -> offset++
                '"' -> return summaryJson.decodeFromString(json.substring(start, offset))
            }
        }
        error("Invalid JSON string")
    }
    fun value(depth: Int) {
        require(depth < 16)
        space()
        require(offset < json.length)
        when (json[offset]) {
            '{' -> {
                offset++; space()
                val keys = mutableSetOf<String>()
                if (json.getOrNull(offset) != '}') while (true) {
                    space(); require(json.getOrNull(offset) == '"')
                    require(keys.add(string())) { "Duplicate JSON field" }
                    space(); require(json.getOrNull(offset++) == ':'); value(depth + 1); space()
                    if (json.getOrNull(offset) != ',') break
                    offset++
                }
                require(json.getOrNull(offset++) == '}')
            }
            '[' -> {
                offset++; space()
                if (json.getOrNull(offset) != ']') while (true) {
                    value(depth + 1); space()
                    if (json.getOrNull(offset) != ',') break
                    offset++
                }
                require(json.getOrNull(offset++) == ']')
            }
            '"' -> string()
            else -> { val start = offset; while (offset < json.length && json[offset] !in ",]} \r\n\t") offset++; require(offset > start) }
        }
    }
    value(0); space(); require(offset == json.length)
}
