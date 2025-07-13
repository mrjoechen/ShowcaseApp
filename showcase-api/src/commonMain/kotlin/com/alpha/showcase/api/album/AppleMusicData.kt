package com.alpha.showcase.repo.album

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppleMusicData(
    @SerialName("data")
    val data: AppleMusicDataContent
)

@Serializable
data class AppleMusicDataContent(
    @SerialName("sections")
    val sections: List<Section>,
    @SerialName("canonicalURL")
    val canonicalURL: String,
)

@Serializable
data class Section(
    @SerialName("id")
    val id: String,
    @SerialName("itemKind")
    val itemKind: String,
    @SerialName("items")
    val items: List<Item>? = null
)

@Serializable
data class Item(
    @SerialName("id")
    val id: String,
    @SerialName("artwork")
    val artwork: Artwork? = null,
    @SerialName("title")
    val title: String ?= null
)

@Serializable
data class Artwork(
    @SerialName("dictionary")
    val dictionary: ArtworkDictionary
)

@Serializable
data class ArtworkDictionary(
    @SerialName("width")
    val width: Int,
    @SerialName("url")
    val url: String,
    @SerialName("height")
    val height: Int,
    @SerialName("textColor3")
    val textColor3: String? = null,
    @SerialName("textColor2")
    val textColor2: String? = null,
    @SerialName("textColor4")
    val textColor4: String? = null,
    @SerialName("textColor1")
    val textColor1: String? = null,
    @SerialName("bgColor")
    val bgColor: String? = null,
    @SerialName("hasP3")
    val hasP3: Boolean = false
)


fun AppleMusicData.albumList(): List<AppleMusicAlbum> {
    return data.sections.find { it.itemKind.contains("trackLockup") }?.let{ section ->
        section.items?.mapNotNull { item ->
            item.artwork?.let { artwork ->
                AppleMusicAlbum(
                    id = item.id,
                    title = item.title?: "Unknown Title",
                    artworkUrl = artwork.dictionary.url.replace("{w}x{h}", "${artwork.dictionary.width}x${artwork.dictionary.height}").replace("{f}", "webp"),
                    width = artwork.dictionary.width,
                    height = artwork.dictionary.height
                )
            }
        } ?: emptyList()
    }?: emptyList()
}


data class AppleMusicAlbum(
    val id: String,
    val title: String,
    val artworkUrl: String,
    val width: Int,
    val height: Int
)
