package com.alpha.showcase.api.unsplash

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Photo(
    @SerialName("id")
    val id: String,
    @SerialName("width")
    val width: Int,
    @SerialName("height")
    val height: Int,
    @SerialName("description")
    val description: String?,
    @SerialName("urls")
    val urls: PhotoUrls // 包含原始、大、中等和缩略图URL的集合
)

@Serializable
data class UserCollection(
    @SerialName("id")
    val id: String,
    @SerialName("title")
    val title: String,
    @SerialName("description")
    val description: String?,
    @SerialName("published_at")
    val published_at: String?,
    @SerialName("updated_at")
    val updated_at: String?,
    @SerialName("featured")
    val featured: Boolean?,
    @SerialName("total_photos")
    val total_photos: Int?,
    @SerialName("private")
    val private: Boolean?,
    @SerialName("share_key")
    val share_key: String?,
    @SerialName("cover_photo")
    val cover_photo: Photo?,
    @SerialName("preview_photos")
    val preview_photos: List<PhotoPreview>?,
    @SerialName("links")
    val links: CollectionLinks?
)

@Serializable
data class CollectionLinks(
    @SerialName("self")
    val self: String?,
    @SerialName("html")
    val html: String?,
    @SerialName("photos")
    val photos: String?,
    @SerialName("related")
    val related: String?
)

@Serializable
data class PhotoPreview(
    @SerialName("id")
    val id: String?,
    @SerialName("urls")
    val urls: PhotoUrls?
)

@Serializable
data class PhotoUrls(
    @SerialName("raw")
    val raw: String?,
    @SerialName("full")
    val full: String?,
    @SerialName("regular")
    val regular: String?,
    @SerialName("small")
    val small: String?,
    @SerialName("thumb")
    val thumb: String?
)


data class Topic(val id: String, val slug: String)
