package com.alpha.showcase.common.ai

internal actual suspend fun importSummaryArchive(repository: DatabaseSummaryRepository): SummaryImportResult? = error("AI is unavailable in the browser")
internal actual suspend fun exportSummaryFile(repository: DatabaseSummaryRepository, request: SummaryExportRequest): SummaryArchiveCounts? = error("AI is unavailable in the browser")
