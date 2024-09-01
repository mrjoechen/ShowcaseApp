package com.alpha.showcase.common

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.core.context.startKoin

object Startup {
	fun run() {
		Napier.base(DebugAntilog())
	}
}