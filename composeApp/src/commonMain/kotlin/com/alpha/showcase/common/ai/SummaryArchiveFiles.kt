package com.alpha.showcase.common.ai

internal expect suspend fun importSummaryArchive(repository: DatabaseSummaryRepository): SummaryImportResult?

internal enum class SummaryExportFormat(val extension: String) { Archive("scsummary"), Csv("csv") }

/** Immutable options captured before preparing the file or opening the platform picker. */
internal data class SummaryExportRequest(
    val format: SummaryExportFormat = SummaryExportFormat.Archive,
    val includeDiagnostics: Boolean = false,
)

internal expect suspend fun exportSummaryFile(repository: DatabaseSummaryRepository, request: SummaryExportRequest): SummaryArchiveCounts?
