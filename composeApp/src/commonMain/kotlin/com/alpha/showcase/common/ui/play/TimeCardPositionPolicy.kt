package com.alpha.showcase.common.ui.play

import androidx.compose.ui.Alignment

internal class TimeCardPositionPolicy(
    isPortrait: Boolean,
    avoidImageSummary: Boolean = false,
) {
    private val useCenterPositions = isPortrait && !avoidImageSummary

    val positions: List<Alignment> = if (useCenterPositions) {
        listOf(Alignment.TopCenter, Alignment.BottomCenter)
    } else {
        listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomEnd)
    }

    val initialPosition: Alignment = if (useCenterPositions) Alignment.BottomCenter else Alignment.BottomEnd

    fun resolve(currentPosition: Alignment): Alignment =
        currentPosition.takeIf { it in positions } ?: initialPosition
}
