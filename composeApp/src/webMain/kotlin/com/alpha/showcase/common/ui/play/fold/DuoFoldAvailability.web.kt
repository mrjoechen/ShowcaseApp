package com.alpha.showcase.common.ui.play.fold

import androidx.compose.runtime.Composable

// JS/Wasm share Skia APIs, but that alone does not validate browser support for
// this combination of nested layer recording, blur and blend masks. Opt in only
// after validating both browser backends; do not infer support from compilation.
@Composable
internal actual fun platformSupportsDuoFold(): Boolean = false
