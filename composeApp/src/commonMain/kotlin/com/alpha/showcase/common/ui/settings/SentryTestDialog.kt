package com.alpha.showcase.common.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.alpha.showcase.common.ui.view.IconItem
import com.alpha.showcase.common.utils.SentryTestResult
import getPlatform
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.*
import testCaptureError

@Composable
internal fun SentryTestItem(onClick: () -> Unit) {
    IconItem(
        Icons.Outlined.BugReport,
        desc = stringResource(Res.string.sentry_test_title),
        onClick = onClick,
    )
}

@Composable
internal fun SentryTestDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var submitting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SentryTestResult?>(null) }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(Res.string.sentry_test_title)) },
        text = {
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(getPlatform().platform.platformName)
                    Text(stringResource(Res.string.sentry_test_description))
                    val currentResult = result
                    if (currentResult != null) {
                        Text(when (currentResult) {
                            SentryTestResult.ConsentRequired -> stringResource(Res.string.sentry_test_consent)
                            SentryTestResult.Unsupported -> stringResource(Res.string.sentry_test_unsupported)
                            SentryTestResult.NotInitialized -> stringResource(Res.string.sentry_test_not_initialized)
                            SentryTestResult.Rejected -> stringResource(Res.string.sentry_test_rejected)
                            SentryTestResult.Failed -> stringResource(Res.string.sentry_test_failed)
                            is SentryTestResult.Queued -> stringResource(Res.string.sentry_test_queued, currentResult.eventId)
                        })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    submitting = true
                    result = null
                    scope.launch {
                        try {
                            result = testCaptureError()
                        } finally {
                            submitting = false
                        }
                    }
                },
            ) {
                Text(stringResource(if (submitting) Res.string.loading else Res.string.sentry_test_send))
            }
        },
        dismissButton = {
            TextButton(enabled = !submitting, onClick = onDismiss) {
                Text(stringResource(Res.string.sentry_test_close))
            }
        },
    )
}
