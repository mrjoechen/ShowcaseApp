package com.alpha.showcase.api.immich

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class LoginRequest(
    @SerialName("email")
    val email: String,
    @SerialName("password")
    val password: String
)
@Serializable
data class LoginResponse(
    @SerialName("message")
    val message: String?,
    @SerialName("error")
    val error: String?,
    @SerialName("statusCode")
    val statusCode: Int?,
    @SerialName("accessToken")
    val accessToken: String?,
    @SerialName("isAdmin")
    val isAdmin: Boolean?,
    @SerialName("name")
    val name: String?,
    @SerialName("profileImagePath")
    val profileImagePath: String?,
    @SerialName("shouldChangePassword")
    val shouldChangePassword: Boolean?,
    @SerialName("userEmail")
    val userEmail: String?,
    @SerialName("userId")
    val userId: String?
)

@Serializable
data class Album(
    @SerialName("albumName")
    val albumName: String,
    @SerialName("description")
    val description: String,
    @SerialName("albumThumbnailAssetId")
    val albumThumbnailAssetId: String?,
    @SerialName("createdAt")
    val createdAt: String,
    @SerialName("updatedAt")
    val updatedAt: String,
    @SerialName("id")
    val id: String,
    @SerialName("ownerId")
    val ownerId: String,
    @SerialName("owner")
    val owner: Owner,
    @SerialName("shared")
    val shared: Boolean,
    @SerialName("hasSharedLink")
    val hasSharedLink: Boolean,
    @SerialName("startDate")
    val startDate: String? = null,
    @SerialName("endDate")
    val endDate: String? = null,
    @SerialName("assets")
    val assets: List<Asset>,
    @SerialName("assetCount")
    val assetCount: Int,
    @SerialName("isActivityEnabled")
    val isActivityEnabled: Boolean,
    @SerialName("order")
    val order: String,
    @SerialName("lastModifiedAssetTimestamp")
    val lastModifiedAssetTimestamp: String? = null
)

@Serializable
data class Owner(
    @SerialName("id")
    val id: String,
    @SerialName("email")
    val email: String,
    @SerialName("name")
    val name: String,
    @SerialName("profileImagePath")
    val profileImagePath: String,
    @SerialName("avatarColor")
    val avatarColor: String,
    @SerialName("profileChangedAt")
    val profileChangedAt: String
)

@Serializable
data class Asset(
    @SerialName("id")
    val id: String,
    @SerialName("deviceAssetId")
    val deviceAssetId: String,
    @SerialName("ownerId")
    val ownerId: String,
    @SerialName("deviceId")
    val deviceId: String,
    @SerialName("libraryId")
    val libraryId: String?,
    @SerialName("type")
    val type: String,
    @SerialName("originalPath")
    val originalPath: String,
    @SerialName("originalFileName")
    val originalFileName: String,
    @SerialName("originalMimeType")
    val originalMimeType: String,
    @SerialName("thumbhash")
    val thumbhash: String,
    @SerialName("fileCreatedAt")
    val fileCreatedAt: String,
    @SerialName("fileModifiedAt")
    val fileModifiedAt: String,
    @SerialName("localDateTime")
    val localDateTime: String,
    @SerialName("updatedAt")
    val updatedAt: String,
    @SerialName("isFavorite")
    val isFavorite: Boolean,
    @SerialName("isArchived")
    val isArchived: Boolean,
    @SerialName("isTrashed")
    val isTrashed: Boolean,
    @SerialName("duration")
    val duration: String,
    @SerialName("exifInfo")
    val exifInfo: ExifInfo?,
    @SerialName("livePhotoVideoId")
    val livePhotoVideoId: String?,
    @SerialName("checksum")
    val checksum: String,
    @SerialName("isOffline")
    val isOffline: Boolean,
    @SerialName("hasMetadata")
    val hasMetadata: Boolean,
    @SerialName("duplicateId")
    val duplicateId: String?,
    @SerialName("resized")
    val resized: Boolean
)

@Serializable
data class ExifInfo(
    @SerialName("make")
    val make: String?,
    @SerialName("model")
    val model: String?,
    @SerialName("exifImageWidth")
    val exifImageWidth: Int?,
    @SerialName("exifImageHeight")
    val exifImageHeight: Int?,
    @SerialName("fileSizeInByte")
    val fileSizeInByte: Long?,
    @SerialName("orientation")
    val orientation: String?,
    @SerialName("dateTimeOriginal")
    val dateTimeOriginal: String?,
    @SerialName("modifyDate")
    val modifyDate: String?,
    @SerialName("timeZone")
    val timeZone: String?,
    @SerialName("lensModel")
    val lensModel: String?,
    @SerialName("fNumber")
    val fNumber: Float?,
    @SerialName("focalLength")
    val focalLength: Float?,
    @SerialName("iso")
    val iso: Int?,
    @SerialName("exposureTime")
    val exposureTime: String?,
    @SerialName("latitude")
    val latitude: Double?,
    @SerialName("longitude")
    val longitude: Double?,
    @SerialName("city")
    val city: String?,
    @SerialName("state")
    val state: String?,
    @SerialName("country")
    val country: String?,
    @SerialName("description")
    val description: String,
    @SerialName("projectionType")
    val projectionType: String?,
    @SerialName("rating")
    val rating: Int?
)