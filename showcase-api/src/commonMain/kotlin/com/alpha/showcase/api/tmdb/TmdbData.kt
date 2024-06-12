package com.alpha.showcase.api.tmdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MovieListResponse(
  @SerialName("page") val page: Int,
  @SerialName("results") val results: List<Movie>,
  @SerialName("total_pages") val totalPages: Int,
  @SerialName("total_results") val totalResults: Int
)
@Serializable
data class Movie(
  @SerialName("id") val id: Int,
  @SerialName("title") val title: String,
  @SerialName("overview") val overview: String,
  @SerialName("poster_path") val posterPath: String?,
  @SerialName("backdrop_path") val backdropPath: String?,
  @SerialName("vote_average") val voteAverage: Double,
  @SerialName("release_date") val releaseDate: String
)
@Serializable
data class MovieImagesResponse(
  @SerialName("id") val id: Int,
  @SerialName("backdrops") val backdrops: List<Image>,
  @SerialName("posters") val posters: List<Image>
)
@Serializable
data class Image(
  @SerialName("file_path") val filePath: String,
  @SerialName("width") val width: Int,
  @SerialName("height") val height: Int,
  @SerialName("iso_639_1") val iso6391: String?,
  @SerialName("aspect_ratio") val aspectRatio: Double,
  @SerialName("vote_average") val voteAverage: Double,
  @SerialName("vote_count") val voteCount: Int
)