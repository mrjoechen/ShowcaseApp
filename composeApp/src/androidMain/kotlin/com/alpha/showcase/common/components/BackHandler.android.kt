package com.alpha.showcase.common.components

import androidx.compose.runtime.Composable
import currentActivity

@Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Boolean) {
	androidx.activity.compose.BackHandler(enabled){
		if (!onBack()) {
			currentActivity?.finish()
		}
	}
}