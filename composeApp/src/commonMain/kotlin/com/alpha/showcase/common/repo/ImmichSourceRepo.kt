package com.alpha.showcase.common.repo

import com.alpha.showcase.api.immich.ImmichApi
import com.alpha.showcase.api.immich.LoginRequest
import com.alpha.showcase.common.networkfile.storage.remote.IMMICH_AUTH_TYPE_API_KEY
import com.alpha.showcase.common.networkfile.storage.remote.IMMICH_AUTH_TYPE_BEARER
import com.alpha.showcase.common.networkfile.storage.remote.ImmichSource
import com.alpha.showcase.common.networkfile.util.RConfig.decrypt
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.utils.SUPPORT_MIME_FILTER_VIDEO

class ImmichSourceRepo : SourceRepository<ImmichSource, DataWithType> {

    private val api by lazy {
        ImmichApi()
    }
    override suspend fun getItem(remoteApi: ImmichSource): Result<DataWithType> {
        TODO("Not yet implemented")
    }

    override suspend fun getItems(
        remoteApi: ImmichSource,
        recursive: Boolean,
        filter: ((DataWithType) -> Boolean)?
    ): Result<List<DataWithType>> {
        return try {
            when(remoteApi.authType){
                IMMICH_AUTH_TYPE_API_KEY -> {
                    val albums = api.getAlbumsWithApikey(baseUrl = remoteApi.url, remoteApi.apiKey!!)
                    if (albums.isNullOrEmpty()) {
                        Result.failure(Exception("Invalid api key"))
                    }else albums.find {
                        it.albumName == remoteApi.album
                    }?.let { album ->
                        val result = api.getAlbumWithApikey(baseUrl = remoteApi.url, album.id, remoteApi.apiKey!!)
                        result?.assets?.map {
                            if (it.originalMimeType in SUPPORT_MIME_FILTER_VIDEO){
                                DataWithType(
                                    genVideoUrl(remoteApi.url, it.id),
                                    it.originalMimeType,
                                    mapOf("x-api-key" to remoteApi.apiKey)
                                )
                            }else DataWithType(genImageUrl(remoteApi.url, it.id), it.originalMimeType, mapOf("x-api-key" to remoteApi.apiKey))
                        }?.filter { filter?.invoke(it)?: false }?.let {
                            Result.success(it)
                        }?: Result.failure(Exception("Empty album!"))
                    }?: Result.failure(Exception("${remoteApi.album} Not Found!"))
                }

                IMMICH_AUTH_TYPE_BEARER -> {
                    val loginResponse = api.login(
                        baseUrl = remoteApi.url,
                        LoginRequest(
                            remoteApi.user!!,
                            decrypt(remoteApi.pass!!)
                        )
                    )
                    loginResponse?.accessToken?.let { token ->
                        val bearer = "Bearer $token"
                        val albums = api.getAlbumsWithAccessToken(baseUrl = remoteApi.url, bearer)
                        albums?.find {
                            it.albumName == remoteApi.album
                        }?.let { album ->
                            val result = api.getAlbumWithAccessToken(baseUrl = remoteApi.url, album.id, bearer)
                            result?.assets?.map {
                                if (it.originalMimeType in SUPPORT_MIME_FILTER_VIDEO){
                                    DataWithType(genVideoUrl(remoteApi.url, it.id), it.originalMimeType, mapOf("Authorization" to bearer))
                                }else DataWithType(genImageUrl(remoteApi.url, it.id), it.originalMimeType, mapOf("Authorization" to bearer))
                            }?.filter { filter?.invoke(it)?:false }?.let {
                                Result.success(it)
                            }?: Result.failure(Exception("Empty album!"))
                        }?: Result.failure(Exception("${remoteApi.album} Not Found!"))

                    } ?: Result.failure(Exception("Auth Error ${loginResponse?.message?:""}"))
                }

                else -> {
                    Result.failure(Exception("Unsupported auth type"))
                }
            }
        }catch (ex: Exception){
            Result.failure(ex)
        }
    }


    private fun genImageUrl(url: String, id: String): String {
        return "$url/api/assets/$id/thumbnail?size=preview"
    }

    private fun genVideoUrl(url: String, id: String): String {
        return "$url/api/assets/$id/original"
    }

}