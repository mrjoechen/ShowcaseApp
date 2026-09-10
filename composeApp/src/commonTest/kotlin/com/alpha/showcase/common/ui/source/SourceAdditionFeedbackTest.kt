package com.alpha.showcase.common.ui.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class SourceAdditionFeedbackTest {
    @Test
    fun unreadMarkerAndConsumedCelebrationSurviveReload() {
        val saved = SourceAdditionFeedback().added("first").celebrated()
        val restored = Json.decodeFromString<SourceAdditionFeedback>(
            Json.encodeToString(SourceAdditionFeedback.serializer(), saved),
        )
        assertEquals("first", restored.latestSourceName)
        assertFalse(restored.added("second").celebrationPending)
    }

    @Test
    fun onlyLatestAdditionIsUnread() {
        val state = SourceAdditionFeedback().added("first").added("second")
        assertEquals("second", state.latestSourceName)
        assertEquals(state, state.opened("first"))
        assertNull(state.opened("second").latestSourceName)
    }

    @Test
    fun celebrationIsConsumedOnceEvenAfterDeletingAllSources() {
        val first = SourceAdditionFeedback().added("first")
        assertTrue(first.celebrationPending)
        val consumed = first.celebrated().opened("first")
        assertFalse(consumed.added("second").celebrationPending)
        assertTrue(consumed.hasAddedSource)
    }

    @Test
    fun renamingUnreadSourcePreservesMarker() {
        val state = SourceAdditionFeedback().added("first").renamed("first", "renamed")
        assertEquals("renamed", state.latestSourceName)
        assertNull(state.opened("renamed").latestSourceName)
    }
}
