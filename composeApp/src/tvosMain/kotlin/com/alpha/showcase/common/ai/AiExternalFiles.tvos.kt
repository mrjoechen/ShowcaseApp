package com.alpha.showcase.common.ai

internal actual suspend fun exportAiImage(name: String, bytes: ByteArray): Boolean = false
internal actual suspend fun shareAiImage(name: String, bytes: ByteArray): Unit =
    error("Image sharing is unavailable on Apple TV")

internal actual suspend fun importSummaryArchive(repository: DatabaseSummaryRepository): SummaryImportResult? = null
internal actual suspend fun exportSummaryFile(repository: DatabaseSummaryRepository, request: SummaryExportRequest): SummaryArchiveCounts? = null
