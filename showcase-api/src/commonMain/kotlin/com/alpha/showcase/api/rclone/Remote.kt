package com.alpha.showcase.api.rclone

import kotlinx.serialization.Serializable

@Serializable
data class Remote(val key: String, val remoteConfig: RemoteConfig)

fun Remote.isType(storageType: StorageType) = remoteConfig.type.uppercase() == storageType.typeName