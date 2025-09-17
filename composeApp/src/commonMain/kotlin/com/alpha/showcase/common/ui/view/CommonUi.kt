@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.ui.view

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.alpha.showcase.common.ui.ext.handleBackKey
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Composable
fun BackKeyHandler(
    onBack: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val focusRequester = remember(onBack){ FocusRequester() }
    var threshold by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .handleBackKey {
                if (kotlin.time.Clock.System.now().toEpochMilliseconds() - threshold > 1000L) {
                    onBack()
                }
                threshold = Clock.System.now().toEpochMilliseconds()
            }
    ) {

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
        content()
    }
}