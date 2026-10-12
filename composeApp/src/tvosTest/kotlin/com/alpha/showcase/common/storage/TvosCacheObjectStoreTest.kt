package com.alpha.showcase.common.storage

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class TvosCacheObjectStoreTest {
    @Serializable
    private data class Entry(val value: String)

    private val fileSystem = FakeFileSystem()
    private val directory = "/Caches/object_store".toPath()
    private val path = directory / "nested" / "entry.json"
    private val store = TvosCacheObjectStore(path, serializer<Entry>(), fileSystem)

    @Test
    fun roundTripsAndDeletes() = runTest {
        assertNull(store.get())
        store.set(Entry("location"))
        assertEquals(Entry("location"), store.get())
        store.delete()
        assertNull(store.get())
    }

    @Test
    fun reclaimedDirectoryIsRecreatedOnWrite() = runTest {
        store.set(Entry("before"))
        fileSystem.deleteRecursively(directory)
        assertNull(store.get())
        store.set(Entry("after"))
        assertEquals(Entry("after"), store.get())
    }

    @Test
    fun unreadableCacheIsDiscarded() = runTest {
        fileSystem.createDirectories(path.parent!!)
        fileSystem.write(path) { writeUtf8("{not json") }
        assertNull(store.get())
        assertFalse(fileSystem.exists(path))
    }
}
