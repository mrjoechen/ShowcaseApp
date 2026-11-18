package com.alpha.showcase.common.ui.play

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChineseLunarDateTest {
    @Test
    fun referenceMonthAndLunarNewYear() {
        assertEquals(ChineseLunarDate(10, 1, false), LocalDate(2026, 11, 9).toChineseLunarDate())
        assertEquals(ChineseLunarDate(10, 10, false), LocalDate(2026, 11, 18).toChineseLunarDate())
        assertEquals(ChineseLunarDate(1, 1, false), LocalDate(2026, 2, 17).toChineseLunarDate())
    }

    @Test
    fun distinguishesRepeatedMonthFromRegularMonth() {
        assertEquals(ChineseLunarDate(6, 1, false), LocalDate(2025, 6, 25).toChineseLunarDate())
        assertEquals(ChineseLunarDate(6, 1, true), LocalDate(2025, 7, 25).toChineseLunarDate())
        assertEquals(ChineseLunarDate(7, 1, false), LocalDate(2025, 8, 23).toChineseLunarDate())
    }

    @Test
    fun omitsLunarCaptionOutsideTheConversionTable() {
        assertNull(LocalDate(1900, 1, 30).toChineseLunarDate())
        assertEquals(ChineseLunarDate(1, 1, false), LocalDate(1900, 1, 31).toChineseLunarDate())
        assertNull(LocalDate(2101, 1, 29).toChineseLunarDate())
    }
}
