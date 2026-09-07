package com.alpha.showcase.common.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class DurableDeviceIdEncodingTest {
    @Test
    fun keychainDataPreservesTheDeviceUuid() {
        val deviceId = "00000000-0000-4000-8000-000000000001"
        assertEquals(deviceId, deviceIdFromKeychainData(deviceIdToKeychainData(deviceId)))
    }
}
