package com.alpha.showcase.common.storage

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

class IosObjectStoreSharingTest {
    @Test
    fun handlesForTheSameKeySeeEachOthersWrites() = runTest {
        val key = "sharing_test_${Uuid.random()}"
        val writer = objectStoreOf<String>(key)
        val reader = objectStoreOf<String>(key)
        try {
            writer.set("before sync")
            assertEquals("before sync", reader.get())

            // Sync writes sources through its own repository while the source list reads through another.
            writer.set("after sync")
            assertEquals("after sync", reader.get())
        } finally {
            writer.delete()
        }
    }

    @Test
    fun aKeyCannotBeReusedForAnotherType() {
        val key = "type_test_${Uuid.random()}"
        objectStoreOf<String>(key)
        assertFailsWith<IllegalStateException> { objectStoreOf<Int>(key) }
    }
}
