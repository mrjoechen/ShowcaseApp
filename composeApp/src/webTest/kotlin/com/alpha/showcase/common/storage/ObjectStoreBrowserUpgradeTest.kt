package com.alpha.showcase.common.storage

import kotlinx.browser.localStorage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ObjectStoreBrowserUpgradeTest {
    @AfterTest
    fun cleanUp() = localStorage.removeItem(KEY)

    @Test
    fun existingRawStringSurvivesUpdateAndReload() = runTest {
        localStorage.setItem(KEY, "legacy-device-id")
        assertEquals("legacy-device-id", objectStoreOf<String>(KEY).get())
        objectStoreOf<String>(KEY).set("updated-设备-id")
        assertEquals("updated-设备-id", objectStoreOf<String>(KEY).get())
        objectStoreOf<String>(KEY).delete()
        assertNull(objectStoreOf<String>(KEY).get())
    }

    @Test
    fun existingJsonObjectSurvivesUpdateAndReload() = runTest {
        localStorage.setItem(KEY, """{"enabled":true,"label":"旧配置"}""")
        assertEquals(Setting(true, "旧配置"), objectStoreOf<Setting>(KEY).get())
        objectStoreOf<Setting>(KEY).set(Setting(false, "新配置"))
        assertEquals(Setting(false, "新配置"), objectStoreOf<Setting>(KEY).get())
        objectStoreOf<Setting>(KEY).delete()
        assertNull(objectStoreOf<Setting>(KEY).get())
    }

    @Serializable
    private data class Setting(val enabled: Boolean, val label: String)

    private companion object {
        const val KEY = "showcase_test_dependency_upgrade_store"
    }
}
