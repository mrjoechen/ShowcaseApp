package com.alpha.showcase.common.ui.settings

import PLATFORM_TYPE
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentationUrlTest {

    @Test
    fun applePlatformsUseShowcasePixDocuments() {
        assertEquals("https://mrjoechen.github.io/ShowcaseApp/showcase-pix", documentationBaseUrl(PLATFORM_TYPE.Ios, "en"))
        assertEquals("https://mrjoechen.github.io/ShowcaseApp/showcase-pix/zh", documentationBaseUrl(PLATFORM_TYPE.Tvos, "zh"))
    }

    @Test
    fun otherPlatformsUseShowcaseDocuments() {
        assertEquals("https://mrjoechen.github.io/ShowcaseApp", documentationBaseUrl(PLATFORM_TYPE.Android, "fr"))
        assertEquals("https://mrjoechen.github.io/ShowcaseApp/zh", documentationBaseUrl(PLATFORM_TYPE.Desktop, "ZH"))
    }
}
