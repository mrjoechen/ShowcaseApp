package com.alpha.showcase.api.pexels.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Src(
	@SerialName("landscape")
	val landscape: String,
	@SerialName("large")
	val large: String,
	@SerialName("large2x")
	val large2x: String,
	@SerialName("medium")
	val medium: String,
	@SerialName("original")
	val original: String,
	@SerialName("portrait")
	val portrait: String,
	@SerialName("small")
	val small: String,
	@SerialName("tiny")
	val tiny: String
)