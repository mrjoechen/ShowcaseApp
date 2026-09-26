package com.alpha.showcase.common.ai

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import com.alpha.showcase.common.ui.ai.AiSummaryExportDialog
import org.jetbrains.compose.resources.getString
import showcaseapp.composeapp.generated.resources.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class AiSummaryExportDialogTest {
    @Test fun defaultsToArchiveWithoutDiagnosticsAndConfirmsSelectedOptions() = runDesktopComposeUiTest(width = 700, height = 800) {
        var request: SummaryExportRequest? = null
        setContent { MaterialTheme { AiSummaryExportDialog({}, { request = it }) } }
        onNodeWithText(getString(Res.string.ai_summary_export_format_archive)).assertIsSelected()
        onNodeWithText(getString(Res.string.ai_summary_export_diagnostics)).assertIsOff().performClick()
        onNodeWithText(getString(Res.string.ai_summary_document_continue)).performClick()
        runOnIdle { assertEquals(SummaryExportRequest(SummaryExportFormat.Archive, true), request) }
    }

    @Test fun csvHidesAndClearsDiagnosticsAndFreezesSelection() = runDesktopComposeUiTest(width = 700, height = 800) {
        var request: SummaryExportRequest? = null
        setContent { MaterialTheme { AiSummaryExportDialog({}, { request = it }) } }
        val archive = getString(Res.string.ai_summary_export_format_archive)
        val csv = getString(Res.string.ai_summary_export_format_csv)
        val diagnostics = getString(Res.string.ai_summary_export_diagnostics)
        onNodeWithText(diagnostics).performClick()
        onNodeWithText(csv).performClick().assertIsSelected()
        onNodeWithText(diagnostics).assertDoesNotExist()
        onNodeWithText(getString(Res.string.ai_summary_export_csv_explanation)).assertExists()
        onNodeWithText(getString(Res.string.ai_summary_document_continue)).performClick()
        runOnIdle { assertEquals(SummaryExportRequest(SummaryExportFormat.Csv, false), request) }
        onNodeWithText(archive).performClick()
        onNodeWithText(diagnostics).assertIsOff()
        runOnIdle { assertEquals(SummaryExportRequest(SummaryExportFormat.Csv, false), request) }
    }

    @Test fun cancellingDoesNotStartExport() = runDesktopComposeUiTest(width = 700, height = 800) {
        var dismissed = false
        var exported = false
        setContent { MaterialTheme { AiSummaryExportDialog({ dismissed = true }, { exported = true }) } }
        onNodeWithText(getString(Res.string.ai_summary_cancel)).performClick()
        runOnIdle { assertTrue(dismissed); assertFalse(exported) }
    }
}
