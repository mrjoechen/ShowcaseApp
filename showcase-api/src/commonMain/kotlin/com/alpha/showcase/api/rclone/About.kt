package com.alpha.showcase.api.rclone

import kotlinx.serialization.Serializable

@Serializable
data class About(val used: Long = -1, val free: Long = -1, val total: Long = -1, val trashed: Long = 0)