package com.alpha.showcase.common.ui.config

import kotlin.test.Test
import kotlin.test.assertEquals

class GalleryGridSelectionTest {
    private val uris = List(12) { "photo-$it" }

    @Test
    fun dragSelectsTheWholeRangeIncludingSkippedItems() {
        val selection = GalleryGridSelection(uris, anchor = 2, initiallySelected = setOf("photo-10"))

        assertEquals(setOf("photo-2", "photo-10"), selection.selectionAt(2))
        assertEquals((2..7).map { uris[it] }.toSet() + "photo-10", selection.selectionAt(7))
    }

    @Test
    fun draggingBackRestoresItemsOutsideTheRangeToTheirOriginalState() {
        val selection = GalleryGridSelection(uris, anchor = 2, initiallySelected = setOf("photo-4", "photo-10"))

        selection.selectionAt(7)

        assertEquals(setOf("photo-2", "photo-3", "photo-4", "photo-10"), selection.selectionAt(3))
        assertEquals(setOf("photo-0", "photo-1", "photo-2", "photo-4", "photo-10"), selection.selectionAt(0))
    }

    @Test
    fun startingOnASelectedPhotoDeselectsTheRangeWithoutTogglingOnReentry() {
        val selection = GalleryGridSelection(uris, anchor = 5, initiallySelected = uris.toSet())

        assertEquals(uris.toSet() - uris.subList(2, 6).toSet(), selection.selectionAt(2))
        assertEquals(uris.toSet() - uris.subList(4, 6).toSet(), selection.selectionAt(4))
        assertEquals(uris.toSet() - uris.subList(2, 6).toSet(), selection.selectionAt(2))
    }
}
