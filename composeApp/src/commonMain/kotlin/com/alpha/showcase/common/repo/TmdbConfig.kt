package com.alpha.showcase.common.repo

import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.*


const val TOP_RATED_MOVIES = "Top Rated"
const val POPULAR_MOVIES = "Popular"
const val UPCOMING_MOVIES = "Upcoming"
const val NOW_PLAYING_MOVIES = "Now Playing"

sealed class TMDBSourceType(val type: String, val titleRes: StringResource){
    data object TopRated : TMDBSourceType(TOP_RATED_MOVIES, Res.string.tmdb_top_rated)
    data object Popular : TMDBSourceType(POPULAR_MOVIES, Res.string.tmdb_popular)
    data object Upcoming : TMDBSourceType(UPCOMING_MOVIES, Res.string.tmdb_upcoming)
    data object NowPlaying : TMDBSourceType(NOW_PLAYING_MOVIES, Res.string.tmdb_now_playing)
}

@Serializable
enum class Region(val value: String, val res: StringResource) {
    US("US", Res.string.tmdb_us),
    UK("UK", Res.string.tmdb_uk),
    CA("CA", Res.string.tmdb_ca),
    AU("AU", Res.string.tmdb_au),
    DE("DE", Res.string.tmdb_de),
    FR("FR", Res.string.tmdb_fr),
    ES("ES", Res.string.tmdb_es),
    IT("IT", Res.string.tmdb_it),
    JP("JP", Res.string.tmdb_jp),
    KR("KR", Res.string.tmdb_kr),
    CN("CN", Res.string.tmdb_cn),
    IN("IN", Res.string.tmdb_in),
    RU("RU", Res.string.tmdb_ru)
}
@Serializable
enum class Language(val value: String, val res: StringResource) {
    ENGLISH_US("en-US", Res.string.tmdb_lang_en_us),
    GERMAN("de-DE", Res.string.tmdb_lang_de_de),
    FRENCH("fr-FR", Res.string.tmdb_lang_fr_fr),
    SPANISH("es-ES", Res.string.tmdb_lang_es_es),
    ITALIAN("it-IT", Res.string.tmdb_lang_it_it),
    JAPANESE("ja-JP", Res.string.tmdb_lang_ja_jp),
    KOREAN("ko-KR", Res.string.tmdb_lang_ko_kr),
    CHINESE("zh-CN", Res.string.tmdb_lang_zh_cn),
    CHINESE_TRADITIONAL("zh-TW", Res.string.tmdb_lang_zh_tw),
    RUSSIAN("ru-RU", Res.string.tmdb_lang_ru_ru)
}

enum class ImageType(val value: String, val res: StringResource) {
    POSTER("Poster", Res.string.tmdb_lang_type_poster),
    BACKDROP("backdrop", Res.string.tmdb_image_type_backdrop)
}