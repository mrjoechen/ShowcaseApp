package com.alpha.showcase.common.ai

internal expect suspend fun importSummaryArchive(repository: DatabaseSummaryRepository): SummaryImportResult?
internal expect suspend fun exportSummaryArchive(repository: DatabaseSummaryRepository): SummaryArchiveCounts?
