package com.alpha.showcase.common.ui.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiTokenHelpTest {

    @Test
    fun providerTokenUrlsUseOfficialAcquisitionPages() {
        assertEquals(
            "https://www.themoviedb.org/settings/api",
            ApiTokenProvider.Tmdb.tokenUrl,
        )
        assertEquals(
            "https://unsplash.com/oauth/applications",
            ApiTokenProvider.Unsplash.tokenUrl,
        )
        assertEquals(
            "https://www.pexels.com/api/key/",
            ApiTokenProvider.Pexels.tokenUrl,
        )
    }
}
