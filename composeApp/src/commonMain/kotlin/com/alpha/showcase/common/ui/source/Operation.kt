package com.alpha.showcase.common.ui.source

sealed class Operation(val name: String = "")
data object Config: Operation("Config")
data object Delete: Operation("Delete")
