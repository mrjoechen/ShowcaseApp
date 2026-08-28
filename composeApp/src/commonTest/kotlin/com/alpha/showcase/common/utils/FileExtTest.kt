package com.alpha.showcase.common.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class FileExtTest {

    @Test
    fun extendedImageAndVideoExtensionsUseStableMimeTypes() {
        val expected = mapOf(
            "asset.svg" to "image/svg+xml",
            "asset.ico" to "image/x-icon",
            "asset.avi" to "video/x-msvideo",
            "asset.wmv" to "video/x-ms-wmv",
            "asset.flv" to "video/x-flv",
            "asset.m4v" to "video/x-m4v",
            "asset.3gp" to "video/3gpp",
        )

        assertEquals(expected, expected.keys.associateWith(::getMimeType))
    }
}
