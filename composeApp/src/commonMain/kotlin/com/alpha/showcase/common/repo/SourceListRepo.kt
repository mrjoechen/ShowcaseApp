package com.alpha.showcase.common.repo

import com.alpha.showcase.api.rclone.toDropboxConfig
import com.alpha.showcase.api.rclone.toGoogleDriveConfig
import com.alpha.showcase.api.rclone.toGooglePhotoConfig
import com.alpha.showcase.api.rclone.toOneDriveConfig
import com.alpha.showcase.common.networkfile.RCloneConfigManager
import com.alpha.showcase.common.networkfile.storage.StorageSources
import com.alpha.showcase.common.networkfile.storage.drive.DropBox
import com.alpha.showcase.common.networkfile.storage.drive.GoogleDrive
import com.alpha.showcase.common.networkfile.storage.drive.GooglePhotos
import com.alpha.showcase.common.networkfile.storage.drive.OneDrive
import com.alpha.showcase.common.networkfile.storage.remote.OAuthRcloneApi
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorage
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.storage.objectStoreOf
import com.alpha.showcase.common.utils.supplyConfig
import com.alpha.showcase.common.versionCode
import com.alpha.showcase.common.versionName
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import rclone
import randomUUID

class SourceListRepo {
    private val store = objectStoreOf<String>("sources")

    private val rclone by lazy {
        rclone()
    }

    private val rcloneConfigManager by lazy {
        RCloneConfigManager(rclone.rCloneConfig)
    }

    private val repoManager by lazy {
        RepoManager()
    }


    private val defaultValue by lazy {
        StorageSources(
            versionCode.toInt(),
            versionName,
            randomUUID(),
            "default",
            Clock.System.now().toEpochMilliseconds(),
            mutableListOf()
        )
    }

    suspend fun getSources(): StorageSources {
        return store.get()?.let {
            StorageSourceSerializer.sourceJson.decodeFromString(StorageSources.serializer(), it)
        } ?: defaultValue
    }


    suspend fun setSources(sources: StorageSources) {
        store.set(
            StorageSourceSerializer.sourceJson.encodeToString(
                StorageSources.serializer(),
                sources
            )
        )
    }

    suspend fun addSource(source: RemoteApi) {
        val sources = getSources()
        sources.sources.add(source)
        setSources(sources)
    }


    suspend fun setUpSourcesAndConfig(remoteApi: RemoteApi) {
        if (remoteApi is RcloneRemoteApi) {
            val sources = store.get()?.let {
                StorageSourceSerializer.sourceJson.decodeFromString(StorageSources.serializer(), it)
            } ?: defaultValue
            sources.sources.forEach {
                if (it.name == remoteApi.name) {
                    try {
                        if (it is RemoteStorage) {
                            rclone.setUpAndWait(it)
                        }
                        if (it is OAuthRcloneApi) {
                            rcloneConfigManager.addSection(it.name, it.supplyConfig())
                        }
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        }
    }

    suspend fun saveSource(remoteApi: RemoteApi): Boolean {
        val storageSources = getSources()
        storageSources.sources.add(remoteApi)
        setSources(storageSources)
        return true
    }

    suspend fun deleteSource(remoteApi: RemoteApi): Boolean {
        val oldSources = getSources()

        val sources = mutableListOf<RemoteApi>()
        sources.addAll(oldSources.sources)
        val remoteStorages = oldSources.sources.filter { ele ->
            ele.name == remoteApi.name
        }
        remoteStorages.forEach { ele ->
            sources.remove(ele)
        }
        val storageSources = StorageSources(
            oldSources.version,
            oldSources.versionName,
            oldSources.id,
            oldSources.sourceName,
            oldSources.timeStamp,
            sources
        )
        setSources(storageSources)
        return true
    }

    suspend fun getSourceFileDirItems(
        remoteApi: RcloneRemoteApi,
        path: String,
    ) = rclone.getFileDirItems(remoteApi, path)

    suspend fun <T: OAuthRcloneApi> linkConnection(
        oAuthRcloneApi: T,
        onRetrieveOauthUrl: (String?) -> Unit
    ): T? {
        val upAndWaitOAuth = rclone.setUpAndWaitOAuth(oAuthRcloneApi.genRcloneOption()) {
            onRetrieveOauthUrl.invoke(it)
        }
        // OAuth Success get Rclone Config, token and etc...
        return if (upAndWaitOAuth) {
            var oauthRcloneApi: OAuthRcloneApi? = null
            rclone.getRemote(oAuthRcloneApi.name) {
                it?.apply {
                    when (oAuthRcloneApi) {
                        is GoogleDrive -> {
                            val googleDriveConfig = remoteConfig.toGoogleDriveConfig()
                            oauthRcloneApi = GoogleDrive(
                                oAuthRcloneApi.name,
                                googleDriveConfig.token,
                                cid = googleDriveConfig.client_id,
                                sid = googleDriveConfig.client_secret,
                                folderId = googleDriveConfig.root_folder_id,
                                scope = googleDriveConfig.scope,
                                path = oAuthRcloneApi.path
                            )
                        }

                        is GooglePhotos -> {
                            val googlePhotos = remoteConfig.toGooglePhotoConfig()
                            oauthRcloneApi = GooglePhotos(
                                oAuthRcloneApi.name,
                                googlePhotos.token,
                                cid = googlePhotos.client_id,
                                sid = googlePhotos.client_secret,
                                path = oAuthRcloneApi.path
                            )
                        }

                        is OneDrive -> {
                            val oneDrive = remoteConfig.toOneDriveConfig()
                            oauthRcloneApi = OneDrive(
                                oAuthRcloneApi.name,
                                oneDrive.token,
                                cid = oneDrive.client_id,
                                sid = oneDrive.client_secret,
                                driveId = oneDrive.drive_id,
                                driveType = oneDrive.drive_type,
                                path = oAuthRcloneApi.path
                            )
                        }

                        is DropBox -> {
                            val dropbox = remoteConfig.toDropboxConfig()
                            oauthRcloneApi = DropBox(
                                oAuthRcloneApi.name,
                                dropbox.token,
                                cid = dropbox.client_id,
                                sid = dropbox.client_secret,
                                path = oAuthRcloneApi.path
                            )
                        }

                        else -> {
                            null
                        }
                    }
                }
            }
            oauthRcloneApi as T?
        } else {
            null
        }
    }

    suspend fun checkConnection(remoteApi: RemoteApi, timeout: Long = 5000): Result<Any> {
        return if (if (remoteApi is RemoteStorage) rclone.setUpAndWait(remoteApi) else true) {
            try {
                withTimeout(timeout) {
                    when (remoteApi) {
                        is RemoteStorage -> {
                            val fileInfo = rclone.getFileDirItems(remoteApi, remoteApi.path)
//              rclone.deleteRemote(remoteApi.name)
                            fileInfo
                        }

                        is OAuthRcloneApi -> {
                            val filesInfo = rclone.getFileDirItems(remoteApi, "")
                            //todo: delete remote
//              rclone.deleteRemote(remoteApi.name)
                            filesInfo
                        }

                        else -> {
                            val result = repoManager.getItems(remoteApi)
                            if (result.isSuccess && result.getOrNull() != null) {
                                Result.success(result.getOrNull()!!)
                            } else {
                                Result.failure(Exception("checkConnection Error"))
                            }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                e.printStackTrace()
                rclone.deleteRemote(remoteApi.name)
                Result.failure(e)
            }
        } else {
            rclone.deleteRemote(remoteApi.name)
            Result.failure(Exception("checkConnection Error"))
        }
    }

}
