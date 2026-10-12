package com.alpha.showcase.common.components

import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TvosBackCallbackTest {
    @Test
    fun repeatedBackUsesWindowHandlersWithoutAnyFocusedUi() {
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput()
        dispatcher.addInput(input)
        val calls = mutableListOf<String>()
        val settings = TvosBackCallback(true, { calls += "settings"; true }, input::backCompleted)
        val ai = TvosBackCallback(true, { calls += "ai"; true }, input::backCompleted)
        dispatcher.addHandler(settings)
        dispatcher.addHandler(ai)
        input.backCompleted()
        ai.remove()
        input.backCompleted()
        assertEquals(listOf("ai", "settings"), calls)
        dispatcher.dispose()
    }

    @Test
    fun unconsumedBackFallsThroughAndRetainsCallbackPrecedence() {
        var fallbackCalls = 0
        val dispatcher = NavigationEventDispatcher(onBackCompletedFallback = { fallbackCalls++ })
        val input = DirectNavigationEventInput()
        dispatcher.addInput(input)
        val calls = mutableListOf<String>()
        val lower = TvosBackCallback(true, { calls += "lower"; false }, input::backCompleted)
        val upper = TvosBackCallback(true, { calls += "upper"; false }, input::backCompleted)
        dispatcher.addHandler(lower)
        dispatcher.addHandler(upper)
        repeat(2) { input.backCompleted() }
        assertEquals(listOf("upper", "lower", "upper", "lower"), calls)
        assertEquals(2, fallbackCalls)
        assertTrue(lower.isBackEnabled)
        assertTrue(upper.isBackEnabled)
        dispatcher.dispose()
    }

    @Test
    fun disabledHandlersAndSeparateWindowsDoNotInterceptBack() {
        val calls = mutableListOf<String>()
        val first = NavigationEventDispatcher(onBackCompletedFallback = { calls += "first" })
        val second = NavigationEventDispatcher(onBackCompletedFallback = { calls += "second" })
        val firstInput = DirectNavigationEventInput()
        val secondInput = DirectNavigationEventInput()
        first.addInput(firstInput)
        second.addInput(secondInput)
        val disabled = TvosBackCallback(false, { error("Disabled handler must not run") }, firstInput::backCompleted)
        val active = TvosBackCallback(true, { calls += "active"; true }, secondInput::backCompleted)
        first.addHandler(disabled)
        second.addHandler(active)
        firstInput.backCompleted()
        secondInput.backCompleted()
        active.remove()
        secondInput.backCompleted()
        assertEquals(listOf("first", "active", "second"), calls)
        first.dispose()
        second.dispose()
    }
}
