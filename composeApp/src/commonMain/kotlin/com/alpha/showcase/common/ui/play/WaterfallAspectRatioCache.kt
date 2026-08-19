package com.alpha.showcase.common.ui.play

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

internal class WaterfallAspectRatioCache(
    private val maxEntries: Int = 512
) {
    private val states = mutableMapOf<String, MutableState<Float>>()
    private val accessOrder = ArrayDeque<String>()

    fun stateFor(key: String, fallbackRatio: Float): MutableState<Float> {
        states[key]?.let { state ->
            touch(key)
            return state
        }

        val state = mutableStateOf(fallbackRatio.validRatioOr(4f / 3f))
        states[key] = state
        accessOrder.addLast(key)
        trimToSize()
        return state
    }

    fun update(state: MutableState<Float>, ratio: Float) {
        if (ratio.isFinite() && ratio > 0f) {
            state.value = ratio
        }
    }

    private fun touch(key: String) {
        accessOrder.remove(key)
        accessOrder.addLast(key)
    }

    private fun trimToSize() {
        val safeMaxEntries = maxEntries.coerceAtLeast(1)
        while (states.size > safeMaxEntries) {
            states.remove(accessOrder.removeFirst())
        }
    }
}

private fun Float.validRatioOr(fallback: Float): Float =
    takeIf { it.isFinite() && it > 0f } ?: fallback
