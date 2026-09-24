package com.alpha.showcase.common.ai

internal actual suspend fun importSummaryArchive(repository: DatabaseSummaryRepository): SummaryImportResult? = error("AI is unavailable in the browser")
internal actual suspend fun exportSummaryArchive(repository: DatabaseSummaryRepository): SummaryArchiveCounts? = error("AI is unavailable in the browser")
