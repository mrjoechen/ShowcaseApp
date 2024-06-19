package com.alpha.showcase.common.utils

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.follow_system
import showcaseapp.composeapp.generated.resources.la_ar
import showcaseapp.composeapp.generated.resources.la_az
import showcaseapp.composeapp.generated.resources.la_be
import showcaseapp.composeapp.generated.resources.la_da
import showcaseapp.composeapp.generated.resources.la_de
import showcaseapp.composeapp.generated.resources.la_en_US
import showcaseapp.composeapp.generated.resources.la_es
import showcaseapp.composeapp.generated.resources.la_fa
import showcaseapp.composeapp.generated.resources.la_fil
import showcaseapp.composeapp.generated.resources.la_fr
import showcaseapp.composeapp.generated.resources.la_hu
import showcaseapp.composeapp.generated.resources.la_in
import showcaseapp.composeapp.generated.resources.la_it
import showcaseapp.composeapp.generated.resources.la_ja
import showcaseapp.composeapp.generated.resources.la_ko
import showcaseapp.composeapp.generated.resources.la_ms
import showcaseapp.composeapp.generated.resources.la_nl
import showcaseapp.composeapp.generated.resources.la_pl
import showcaseapp.composeapp.generated.resources.la_pt_BR
import showcaseapp.composeapp.generated.resources.la_ru
import showcaseapp.composeapp.generated.resources.la_sr
import showcaseapp.composeapp.generated.resources.la_tr
import showcaseapp.composeapp.generated.resources.la_uk
import showcaseapp.composeapp.generated.resources.la_vi
import showcaseapp.composeapp.generated.resources.la_zh_CN
import showcaseapp.composeapp.generated.resources.la_zh_TW

const val SYSTEM_DEFAULT = 0
const val SIMPLIFIED_CHINESE = 1
const val ENGLISH = 2
const val FRENCH = 4
const val GERMAN = 5
const val DANISH = 7
const val SPANISH = 8
const val TURKISH = 9
const val UKRAINIAN = 10
const val RUSSIAN = 11
const val ARABIC = 12
const val PERSIAN = 13
const val INDONESIAN = 14
const val FILIPINO = 15
const val ITALIAN = 16
const val DUTCH = 17
const val PORTUGUESE_BRAZIL = 18
const val JAPANESE = 19
const val POLISH = 20
const val HUNGARIAN = 21
const val MALAY = 22
const val TRADITIONAL_CHINESE = 23
const val VIETNAMESE = 24
const val BELARUSIAN = 25
const val SERBIAN = 31
const val AZERBAIJANI = 32
const val KOREAN = 33

val languageMap: Map<Int, String> = mapOf(
    ENGLISH to "en-US",
    SIMPLIFIED_CHINESE to "zh-CN",
//    ARABIC to "ar",
//    AZERBAIJANI to "az",
//    BELARUSIAN to "be",
//    SIMPLIFIED_CHINESE to "zh-CN",
    TRADITIONAL_CHINESE to "zh-TW",
//    DANISH to "da",
//    DUTCH to "nl",
//    ENGLISH to "en-US",
//    FILIPINO to "fil",
//    FRENCH to "fr",
    GERMAN to "de",
//    HUNGARIAN to "hu",
//    INDONESIAN to "in",
//    ITALIAN to "it",
    JAPANESE to "ja",
    KOREAN to "ko",
    //    MALAY to "ms",
//    PERSIAN to "fa",
//    POLISH to "pl",
//    PORTUGUESE_BRAZIL to "pt-BR",
    RUSSIAN to "ru",
//    SERBIAN to "sr",
//    SPANISH to "es",
//    TURKISH to "tr",
    UKRAINIAN to "uk",
//    VIETNAMESE to "vi",
)

private fun getLanguageNumberByCode(languageCode: String): Int =
    languageMap.entries.find { it.value == languageCode }?.key ?: SYSTEM_DEFAULT

fun getLanguageCodeByNumber(languageNumber: Int): String =
    languageMap[languageNumber] ?: ""

fun getLanguageNumber(): Int {
    return SYSTEM_DEFAULT
}


@Composable
fun getLanguageDesc(language: Int = getLanguageNumber()): String {
    return stringResource(
        when (language) {
            SYSTEM_DEFAULT -> Res.string.follow_system
            SIMPLIFIED_CHINESE -> Res.string.la_zh_CN
            ENGLISH -> Res.string.la_en_US
            FRENCH -> Res.string.la_fr
            GERMAN -> Res.string.la_de
            DANISH -> Res.string.la_da
            SPANISH -> Res.string.la_es
            TURKISH -> Res.string.la_tr
            UKRAINIAN -> Res.string.la_uk
            RUSSIAN -> Res.string.la_ru
            ARABIC -> Res.string.la_ar
            PERSIAN -> Res.string.la_fa
            INDONESIAN -> Res.string.la_in
            FILIPINO -> Res.string.la_fil
            ITALIAN -> Res.string.la_it
            DUTCH -> Res.string.la_nl
            PORTUGUESE_BRAZIL -> Res.string.la_pt_BR
            JAPANESE -> Res.string.la_ja
            POLISH -> Res.string.la_pl
            HUNGARIAN -> Res.string.la_hu
            MALAY -> Res.string.la_ms
            TRADITIONAL_CHINESE -> Res.string.la_zh_TW
            VIETNAMESE -> Res.string.la_vi
            BELARUSIAN -> Res.string.la_be
            SERBIAN -> Res.string.la_sr
            AZERBAIJANI -> Res.string.la_az
            KOREAN -> Res.string.la_ko
            else -> Res.string.follow_system
        }
    )

}