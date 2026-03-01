package com.alpha.showcase.common.networkfile.util

import com.alpha.showcase.common.networkfile.storage.drive.DropBox
import com.alpha.showcase.common.networkfile.storage.drive.GoogleDrive
import com.alpha.showcase.common.networkfile.storage.drive.GooglePhotos
import com.alpha.showcase.common.networkfile.storage.drive.OneDrive
import com.alpha.showcase.common.networkfile.storage.remote.AlbumSource
import com.alpha.showcase.common.networkfile.storage.remote.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.GiteeSource
import com.alpha.showcase.common.networkfile.storage.remote.GallerySource
import com.alpha.showcase.common.networkfile.storage.remote.ImmichSource
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.storage.remote.WeiboSource
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorageImpl
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.Union
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

object StorageSourceSerializer{

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
          subclass(UnSplashSource::class, UnSplashSource.serializer())
          subclass(PexelsSource::class, PexelsSource.serializer())
          subclass(GiteeSource::class, GiteeSource.serializer())
          subclass(ImmichSource::class, ImmichSource.serializer())
          subclass(WeiboSource::class, WeiboSource.serializer())
          subclass(AlbumSource::class, AlbumSource.serializer())
          subclass(GallerySource::class, GallerySource.serializer())
          defaultDeserializer { RemoteStorageImpl.serializer() }
      }
    }
  }
}
