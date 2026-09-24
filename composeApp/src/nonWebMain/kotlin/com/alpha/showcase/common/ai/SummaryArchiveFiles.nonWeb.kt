package com.alpha.showcase.common.ai

import io.github.vinceglb.filekit.*
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.io.readByteArray
import okio.Buffer
import okio.FileSystem
import okio.Source
import okio.Timeout
import okio.buffer
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal expect fun prepareSummaryFileDialogs()

internal actual suspend fun importSummaryArchive(repository: DatabaseSummaryRepository): SummaryImportResult? {
    prepareSummaryFileDialogs()
    val file = FileKit.openFilePicker(type = FileKitType.File(extensions = listOf("scsummary"))) ?: return null
    return withContext(Dispatchers.Default) {
        val scoped = file.startAccessingSecurityScopedResource()
        try {
            val context = currentCoroutineContext()
            val raw = file.source()
            val source = object : Source {
                private val buffer = kotlinx.io.Buffer()
                override fun read(sink: Buffer, byteCount: Long): Long {
                    context.ensureActive()
                    if (byteCount == 0L) return 0
                    val count = raw.readAtMostTo(buffer, minOf(byteCount, 64 * 1024L))
                    if (count > 0) sink.write(buffer.readByteArray(count.toInt()))
                    return count
                }
                override fun timeout() = Timeout.NONE
                override fun close() = raw.close()
            }
            val temporary = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "showcase-summary-import-${Uuid.random()}.scsummary"
            try {
                // Finish potentially slow document-provider reads before taking a database transaction.
                source.buffer().use { input ->
                    FileSystem.SYSTEM.sink(temporary).buffer().use { output ->
                        val buffer = Buffer()
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer, 64 * 1024L)
                            if (read == -1L) break
                            total += read
                            require(total <= SUMMARY_ARCHIVE_LIMIT) { "Summary archive too large" }
                            output.write(buffer, read)
                        }
                    }
                }
                FileSystem.SYSTEM.source(temporary).buffer().use { repository.import(it) }
            } finally { FileSystem.SYSTEM.delete(temporary, mustExist = false) }
        } finally { if (scoped) file.stopAccessingSecurityScopedResource() }
    }
}

internal actual suspend fun exportSummaryArchive(repository: DatabaseSummaryRepository): SummaryArchiveCounts? {
    prepareSummaryFileDialogs()
    val temporary = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "showcase-summary-export-${Uuid.random()}.scsummary"
    try {
        val counts = withContext(Dispatchers.Default) {
            FileSystem.SYSTEM.sink(temporary).buffer().use { repository.export(it) }
        }
        // Prepare the entire consistent archive before touching a user-selected destination.
        val target = FileKit.openFileSaver(
            suggestedName = "showcase-summaries-${Clock.System.now().toEpochMilliseconds()}",
            defaultExtension = "scsummary", allowedExtensions = setOf("scsummary"),
        ) ?: return null
        val scoped = target.startAccessingSecurityScopedResource()
        try { target.write(PlatformFile(temporary.toString())) }
        finally { if (scoped) target.stopAccessingSecurityScopedResource() }
        return counts
    } finally {
        FileSystem.SYSTEM.delete(temporary, mustExist = false)
    }
}
