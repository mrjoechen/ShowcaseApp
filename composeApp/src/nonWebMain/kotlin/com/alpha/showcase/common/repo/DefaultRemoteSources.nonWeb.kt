package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource

internal actual fun defaultRemoteSources(): MutableList<RemoteApi> = mutableListOf(
    UnSplashSource("Sample", UnSplashSourceType.UsersPhotos.type, "chenqiao")
)
