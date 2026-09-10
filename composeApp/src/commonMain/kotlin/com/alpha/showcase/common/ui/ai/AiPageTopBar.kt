package com.alpha.showcase.common.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import isDesktop
import isMacOS
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.back

internal val AiPageContentMaxWidth = 640.dp

/** Align desktop navigation with the page's content column and reserve window controls once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiPageTopBar(
    title: String,
    onBack: () -> Unit,
    inDialog: Boolean = false,
    backEnabled: Boolean = true,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    actions: @Composable RowScope.() -> Unit = {},
) {
    // Decorated Windows/Linux windows already reserve their native title bar.
    val needsWindowControlInset = isDesktop() && (inDialog || isMacOS())
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        TopAppBar(
            modifier = if (isDesktop()) {
                // The back icon's own 16.dp inset completes the body's 20.dp padding.
                Modifier.widthIn(max = AiPageContentMaxWidth).fillMaxWidth().padding(horizontal = 4.dp)
            } else Modifier.fillMaxWidth(),
            windowInsets = TopAppBarDefaults.windowInsets.union(
                WindowInsets(top = if (needsWindowControlInset) 36.dp else 0.dp),
            ),
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = onBack, enabled = backEnabled) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back))
                }
            },
            actions = actions,
            colors = colors,
        )
    }
}
