package com.alpha.showcase.common.weather

import kotlin.test.Test
import kotlin.test.assertEquals

class CoarseCoordinateTest {

    @Test
    fun coordinatesSentForWeatherKeepTwoDecimals() {
        assertEquals(31.23, 31.234567.toCoarseCoordinate())
        assertEquals(-122.46, (-122.456789).toCoarseCoordinate())
        assertEquals(0.0, 0.004.toCoarseCoordinate())
    }
}
