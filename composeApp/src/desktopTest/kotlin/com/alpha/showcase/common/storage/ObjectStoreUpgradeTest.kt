package com.alpha.showcase.common.storage

import io.github.xxfast.kstore.file.storeOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObjectStoreUpgradeTest {
    @Test
    fun legacyJsonCanBeUpdatedReloadedAndDeleted() = runTest {
        val directory = Files.createTempDirectory("showcase-kstore-upgrade-").toFile()
        try {
            val file = directory.resolve("setting.json")
            file.writeText("\"legacy-device-id\"")
            fun reopen() = JvmObjectStore(storeOf<String>(Path(file.absolutePath)), file.absolutePath)

            assertEquals("legacy-device-id", reopen().get())
            reopen().set("updated-设备-id")
            assertEquals("updated-设备-id", reopen().get())
            reopen().delete()
            assertNull(reopen().get())
        } finally {
            directory.deleteRecursively()
        }
    }
}
