package com.alpha.showcase.common.ai

import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.back
import kotlin.test.Test
import kotlin.test.assertEquals

class AiProviderResourceTest {
    @Test
    fun backLabelResolvesInEnglish() = runTest {
        assertEquals("Back", getString(environmentFor(Locale.US), Res.string.back))
    }

    @Test
    fun backLabelFallsBackForOtherLocales() = runTest {
        for (locale in listOf(Locale.FRANCE, Locale.TAIWAN, Locale.forLanguageTag("zh-SG"))) {
            assertEquals("Back", getString(environmentFor(locale), Res.string.back), locale.toLanguageTag())
        }
    }

    @Test
    fun backLabelKeepsSimplifiedChineseTranslation() = runTest {
        assertEquals("返回", getString(environmentFor(Locale.SIMPLIFIED_CHINESE), Res.string.back))
    }

    private fun environmentFor(locale: Locale) = synchronized(Locale::class.java) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            getSystemResourceEnvironment()
        } finally {
            Locale.setDefault(original)
        }
    }
}
