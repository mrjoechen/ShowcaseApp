package com.alpha.showcase.api

import com.alpha.showcase.api.pexels.CollectionMediaPage
import com.alpha.showcase.api.pexels.CollectionsPage
import com.alpha.showcase.api.unsplash.Topic
import com.alpha.showcase.api.unsplash.UnsplashOrientation
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalSourceApiModelTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun unsplashOrientationNormalizesStoredValues() {
        assertEquals(UnsplashOrientation.All, UnsplashOrientation.fromStoredValue(null))
        assertEquals(UnsplashOrientation.All, UnsplashOrientation.fromStoredValue("all"))
        assertEquals(UnsplashOrientation.Landscape, UnsplashOrientation.fromStoredValue("landscape"))
        assertEquals(UnsplashOrientation.Portrait, UnsplashOrientation.fromStoredValue("portrait"))
        assertEquals(UnsplashOrientation.Squarish, UnsplashOrientation.fromStoredValue("squarish"))
        assertEquals(UnsplashOrientation.All, UnsplashOrientation.fromStoredValue("wide"))
    }

    @Test
    fun unsplashTopicResponseKeepsDisplayTitleAndSlug() {
        val topic = json.decodeFromString<Topic>(
            """{"id":"bo8jQKTaE0Y","slug":"wallpapers","title":"Wallpapers","featured":true}"""
        )

        assertEquals("bo8jQKTaE0Y", topic.id)
        assertEquals("wallpapers", topic.slug)
        assertEquals("Wallpapers", topic.title)
    }

    @Test
    fun pexelsCollectionResponsesDecodeSelectableCollectionsAndPhotoMedia() {
        val collections = json.decodeFromString<CollectionsPage>(
            """
            {
              "collections": [{
                "id": "9mp14cx",
                "title": "Cool Cats",
                "description": null,
                "private": false,
                "media_count": 6,
                "photos_count": 5,
                "videos_count": 1
              }],
              "page": 1,
              "per_page": 15,
              "total_results": 1
            }
            """.trimIndent()
        )
        val media = json.decodeFromString<CollectionMediaPage>(
            """
            {
              "id": "9mp14cx",
              "page": 1,
              "per_page": 15,
              "total_results": 1,
              "media": [{
                "type": "Photo",
                "id": 42,
                "width": 1600,
                "height": 900,
                "url": "https://pexels.example/photo/42",
                "photographer": "Tester",
                "photographer_url": "https://pexels.example/tester",
                "photographer_id": 7,
                "avg_color": "#000000",
                "src": {
                  "original": "https://images.example/42.jpg",
                  "large2x": "https://images.example/42-large2x.jpg",
                  "large": "https://images.example/42-large.jpg",
                  "medium": "https://images.example/42-medium.jpg",
                  "small": "https://images.example/42-small.jpg",
                  "portrait": "https://images.example/42-portrait.jpg",
                  "landscape": "https://images.example/42-landscape.jpg",
                  "tiny": "https://images.example/42-tiny.jpg"
                },
                "liked": false,
                "alt": "A cat"
              }]
            }
            """.trimIndent()
        )

        assertEquals("9mp14cx", collections.collections.single().id)
        assertEquals("Cool Cats", collections.collections.single().title)
        assertEquals("https://images.example/42.jpg", media.media.single().src.original)
    }
}
