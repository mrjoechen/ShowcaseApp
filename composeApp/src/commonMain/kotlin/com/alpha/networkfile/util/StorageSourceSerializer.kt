package com.alpha.networkfile.util

import com.alpha.networkfile.storage.drive.DropBox
import com.alpha.networkfile.storage.drive.GoogleDrive
import com.alpha.networkfile.storage.drive.GooglePhotos
import com.alpha.networkfile.storage.drive.OneDrive
import com.alpha.networkfile.storage.external.GitHubSource
import com.alpha.networkfile.storage.external.TMDBSource
import com.alpha.networkfile.storage.remote.Ftp
import com.alpha.networkfile.storage.remote.Local
import com.alpha.networkfile.storage.remote.RemoteApi
import com.alpha.networkfile.storage.remote.RemoteApiDefaultImpl
import com.alpha.networkfile.storage.remote.RemoteStorageImpl
import com.alpha.networkfile.storage.remote.Sftp
import com.alpha.networkfile.storage.remote.Smb
import com.alpha.networkfile.storage.remote.Union
import com.alpha.networkfile.storage.remote.WebDav
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

object StorageSourceSerializer {

  val sourceJson = Json {
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
      polymorphic(RemoteApi::class){
          subclass(Smb::class, Smb.serializer())
          subclass(Sftp::class, Sftp.serializer())
          subclass(WebDav::class, WebDav.serializer())
          subclass(Ftp::class, Ftp.serializer())
          subclass(Local::class, Local.serializer())
          subclass(GoogleDrive::class, GoogleDrive.serializer())
          subclass(GooglePhotos::class, GooglePhotos.serializer())
          subclass(OneDrive::class, OneDrive.serializer())
          subclass(DropBox::class, DropBox.serializer())
          subclass(RemoteStorageImpl::class, RemoteStorageImpl.serializer())
          subclass(GitHubSource::class, GitHubSource.serializer())
          subclass(TMDBSource::class, TMDBSource.serializer())
          subclass(Union::class, Union.serializer())

          subclass(RemoteApiDefaultImpl::class, RemoteApiDefaultImpl.serializer())

          defaultDeserializer { RemoteStorageImpl.serializer() }
      }
    }
  }
}