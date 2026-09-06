package com.alpha.showcase.common.ui.config

import com.alpha.showcase.common.utils.ConnectionProbeTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowserConnectionTest {

    @Test
    fun httpsPageDoesNotPreemptivelyRejectHttpBackedDirectSources() {
        assertNull(
            classifyBrowserConnectionProblem(
                pageProtocol = "https:",
            )
        )
    }

    @Test
    fun browserTimeoutAndFetchFailuresExplainBrowserAccessRequirements() {
        listOf(
            ConnectionProbeTimeoutException(10_000),
            IllegalStateException("TypeError: Failed to fetch"),
            IllegalStateException("Request timeout has expired"),
            IllegalStateException("CORS preflight blocked the request"),
        ).forEach { error ->
            assertEquals(
                BrowserConnectionProblem.BrowserAccess,
                classifyBrowserConnectionProblem(
                    pageProtocol = "https:",
                    error = error,
                ),
            )
        }

        assertNull(
            classifyBrowserConnectionProblem(
                pageProtocol = null,
                error = ConnectionProbeTimeoutException(10_000),
            )
        )
    }
}
