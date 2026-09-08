package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.ui.settings.*
import kotlin.test.*

class MediaPresentationPolicyTest {
    @Test fun multiPhotoStylesCannotEnableInformationOrAiActions() {
        val all = MediaOverlayConfig(metadata = true, aiSummary = true, aiGenerate = true)
        listOf(SHOWCASE_MODE_FRAME_WALL, SHOWCASE_MODE_WATERFALL, SHOWCASE_MODE_BENTO,
            SHOWCASE_MODE_SQUARE, SHOWCASE_MODE_CAROUSEL).forEach { style ->
            assertEquals(MediaOverlayConfig.None, all.restrictedToStyle(style))
            assertEquals(MediaOverlayConfig.None, MediaOverlayConfig.forStyle(style))
            assertFalse(supportsKenBurns(style))
        }
    }

    @Test fun singleImageStylesKeepSummarySupportButOnlyFadeAndCalendarMove() {
        listOf(SHOWCASE_MODE_SLIDE, SHOWCASE_MODE_FADE, SHOWCASE_MODE_CALENDER).forEach {
            assertTrue(MediaOverlayConfig.forStyle(it).aiSummary)
        }
        assertFalse(supportsKenBurns(SHOWCASE_MODE_SLIDE))
        assertFalse(supportsKenBurns(-1))
        assertTrue(supportsKenBurns(SHOWCASE_MODE_FADE))
        assertTrue(supportsKenBurns(SHOWCASE_MODE_CALENDER))
    }

    @Test fun pagerAndViewportShareStateButNewMediaVersionsDoNot() {
        val store = MediaItemStateStore()
        val first = DataWithType("photo.jpg", "jpg", mapOf("version" to "1"))
        val second = first.copy(extra = mapOf("version" to "2"))
        val firstState = store.get(10, first)
        firstState.interact(MediaOverlayConfig.forStyle(SHOWCASE_MODE_FADE), true)
        assertSame(firstState, store.get(10, first.copy()))
        val nextState = store.get(10, second)
        assertNotSame(firstState, nextState)
        assertFalse(nextState.showActions)
        assertFalse(nextState.showMetadata)
        assertNull(nextState.displayedImage)
        assertTrue(firstState.showActions)
    }

    @Test fun duplicateMediaInNeighboringPagesCannotOverwriteTheVisiblePresentation() {
        val store = MediaItemStateStore()
        val data = DataWithType("repeated-photo.jpg", "jpg")
        val visible = store.get(10, data)
        visible.retain()
        visible.interact(MediaOverlayConfig.forStyle(SHOWCASE_MODE_SLIDE), true)
        val neighbor = store.get(11, data.copy())
        neighbor.retain()

        assertNotSame(visible, neighbor)
        neighbor.loading()
        assertFalse(visible.loading)
        assertTrue(visible.showMetadata)
        assertTrue(visible.showActions)
        assertFalse(neighbor.showMetadata)
        assertFalse(neighbor.showActions)
        neighbor.failed("Neighbor request failed")
        assertNull(visible.error)
        assertSame(visible, store.get(10, data.copy()))

        neighbor.release()
        visible.release()
    }

    @Test fun capacityCannotEvictARendererOrAnOutgoingOverlayUntilItsLastOwnerLeaves() {
        val store = MediaItemStateStore(capacity = 1)
        val outgoing = store.get(10, "outgoing.jpg")
        outgoing.retain() // Renderer.
        outgoing.retain() // Overlay still fading after the renderer leaves.
        val incoming = store.get(11, "incoming.jpg")
        incoming.retain()

        assertSame(outgoing, store.get(10, "outgoing.jpg"))
        assertSame(incoming, store.get(11, "incoming.jpg"))
        outgoing.release()
        assertSame(outgoing, store.get(10, "outgoing.jpg"))
        outgoing.release()

        // Last release trims the unused entry even without another cache lookup.
        val replacement = store.get(10, "outgoing.jpg")
        assertNotSame(outgoing, replacement)
        assertSame(incoming, store.get(11, "incoming.jpg"))
        incoming.release()
    }

    @Test fun inactiveCachedStatesReleaseTheirDecodedPixelsAfterTheLastLayerLeaves() {
        val state = MediaItemState("photo.jpg")
        state.retain()
        state.retain()
        state.interact(MediaOverlayConfig.forStyle(SHOWCASE_MODE_FADE), true)
        state.release()
        assertTrue(state.showActions)
        state.release()
        assertFalse(state.showActions)
        assertFalse(state.showMetadata)
        assertNull(state.displayedImage)
    }
}
