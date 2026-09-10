package com.alpha.showcase.common.ui.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCreationGridLayoutTest {
    @Test
    fun `four images put the fourth below the first even when the middle column is shorter`() {
        val layout = layout(listOf(0.5f, 2f, 1f, 1f))

        assertEquals(listOf(8, 114, 220, 8), layout.tiles.map { it.x })
        assertEquals(listOf(8, 8, 8, 214), layout.tiles.map { it.y })
        assertEquals(322, layout.height)
    }

    @Test
    fun `five images fill the left and middle columns without aligning row heights`() {
        val layout = layout(listOf(0.5f, 2f, 1f, 1f, 1f))

        assertEquals(listOf(8, 114, 220, 8, 114), layout.tiles.map { it.x })
        assertEquals(listOf(8, 8, 8, 214, 64), layout.tiles.map { it.y })
        assertEquals(322, layout.height)
    }

    @Test
    fun `image dimensions change heights without moving images to another column`() {
        val placeholders = layout(List(5) { 1f })
        val loaded = layout(listOf(0.5f, 2f, 1f, 1f, 1f))

        assertEquals(placeholders.tiles.map { it.x }, loaded.tiles.map { it.x })
        assertEquals(listOf(200, 50, 100, 100, 100), loaded.tiles.map { it.height })
    }

    @Test
    fun `changing column count reflows in source order`() {
        val ratios = listOf(0.5f, 2f, 1f, 1f, 1f)
        val two = calculateAiCreationGridLayout(222, 2, ratios, 8, 6)
        val four = calculateAiCreationGridLayout(434, 4, ratios, 8, 6)

        assertEquals(listOf(8, 114, 8, 114, 8), two.tiles.map { it.x })
        assertEquals(listOf(8, 8, 214, 64, 320), two.tiles.map { it.y })
        assertEquals(listOf(8, 114, 220, 326, 8), four.tiles.map { it.x })
        assertEquals(listOf(8, 8, 8, 8, 214), four.tiles.map { it.y })
    }

    @Test
    fun `visible tiles include a tall earlier image and exclude images outside the viewport`() {
        val layout = layout(listOf(0.25f, 2f, 1f, 1f, 1f, 1f))

        assertEquals(listOf(0, 5), layout.visibleIndices(top = 170, bottom = 230))
        assertEquals(listOf(3), layout.visibleIndices(top = 420, bottom = 520))
    }

    @Test
    fun `empty gallery and partial first row keep their bounds`() {
        assertTrue(layout(emptyList()).tiles.isEmpty())
        assertEquals(16, layout(emptyList()).height)
        assertEquals(listOf(8, 114), layout(listOf(1f, 1f)).tiles.map { it.x })
    }

    private fun layout(ratios: List<Float>) =
        calculateAiCreationGridLayout(328, 3, ratios, 8, 6)
}
