package com.alpha.showcase.common.ui.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DuoFoldAvailabilityTest {
    @Test fun unsupportedPlatformsHideAndResolveSavedDuoFoldAsDefault() {
        assertFalse(SlideEffect.DuoFold in availableSlideEffects(false))
        assertEquals(SlideEffect.Default, effectiveSlideEffect(SlideEffect.DuoFold.value, false))
        assertTrue(SlideEffect.DuoFold in availableSlideEffects(true))
        assertEquals(SlideEffect.DuoFold, effectiveSlideEffect(SlideEffect.DuoFold.value, true))
        for (effect in listOf(SlideEffect.Default, SlideEffect.Cube, SlideEffect.Reveal, SlideEffect.Flip)) {
            assertTrue(effect in availableSlideEffects(false))
            assertEquals(effect, effectiveSlideEffect(effect.value, false))
        }
    }
}
