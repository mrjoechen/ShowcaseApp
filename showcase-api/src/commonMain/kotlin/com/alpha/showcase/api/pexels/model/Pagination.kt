package com.alpha.showcase.api.pexels.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Pagination(
		@SerialName("next_page")
		val nextPage: String,
		@SerialName("page")
		val page: Int,
		@SerialName("per_page")
		val perPage: Int,
		@SerialName("photos")
		val photos: List<Photo>
)
