package com.alpha.showcase.common.ui.ext

import kotlin.test.Test
import kotlin.test.assertEquals

class ExceptionExtTest {

    @Test
    fun clientRequestErrorHidesMethodAndUrl() {
        val error = Exception(
            "Client request(GET http://example.com/private?token=secret) invalid: " +
                "401 Unauthorized. Text: denied"
        )

        assertEquals("Request failed: HTTP 401 Unauthorized", error.getSimpleMessage())
    }
}
