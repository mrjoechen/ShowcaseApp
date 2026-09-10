package com.alpha.showcase.common.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiCreationPinchGestureTest {
    @Test
    fun `one pinch commits at most one column change even after reversing direction`() {
        val gesture = AiCreationPinchGesture()

        assertNull(gesture.onZoom(current = 3, zoomChange = 1.05f))
        assertEquals(2, gesture.onZoom(current = 3, zoomChange = 1.2f))
        assertNull(gesture.onZoom(current = 2, zoomChange = 2f))
        assertNull(gesture.onZoom(current = 2, zoomChange = 0.2f))

        gesture.reset()
        assertEquals(3, gesture.onZoom(current = 2, zoomChange = 0.8f))
        assertNull(gesture.onZoom(current = 3, zoomChange = 0.5f))
    }

    @Test
    fun `releasing a short pinch discards its accumulated zoom`() {
        val gesture = AiCreationPinchGesture()
        assertNull(gesture.onZoom(current = 3, zoomChange = 1.1f))
        gesture.reset()
        assertNull(gesture.onZoom(current = 3, zoomChange = 1.1f))
        assertEquals(2, gesture.onZoom(current = 3, zoomChange = 1.1f))
    }

    @Test
    fun `pinch at column limit cannot reverse into a second adjustment`() {
        val gesture = AiCreationPinchGesture()
        assertNull(gesture.onZoom(current = 1, zoomChange = 2f))
        assertNull(gesture.onZoom(current = 1, zoomChange = 0.1f))
        gesture.reset()
        assertEquals(2, gesture.onZoom(current = 1, zoomChange = 0.8f))
        gesture.reset()
        assertNull(gesture.onZoom(current = 6, zoomChange = 0.1f))
        assertNull(gesture.onZoom(current = 6, zoomChange = 2f))
    }
}
