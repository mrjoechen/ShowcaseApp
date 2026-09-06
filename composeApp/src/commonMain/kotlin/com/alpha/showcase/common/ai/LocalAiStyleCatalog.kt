package com.alpha.showcase.common.ai

import org.jetbrains.compose.resources.StringResource
import showcaseapp.composeapp.generated.resources.*

object LocalAiStyleCatalog {
    private val presets = listOf(
        AiStylePreset(
            key = "ghibli",
            displayNameRes = Res.string.ai_style_ghibli_name,
            descriptionRes = Res.string.ai_style_ghibli_description,
            auditName = "Ghibli Anime",
            prompt = "Transform this into a Studio Ghibli anime style illustration. Hand-painted watercolor background, soft pastel colors, gentle warm lighting, peaceful atmosphere.",
            order = 1,
        ),
        AiStylePreset(
            key = "cyberpunk",
            displayNameRes = Res.string.ai_style_cyberpunk_name,
            descriptionRes = Res.string.ai_style_cyberpunk_description,
            auditName = "Cyberpunk",
            prompt = "Transform this into a futuristic cyberpunk aesthetic. Neon light reflections (blue, purple, pink), glowing elements, high-tech vibe, detailed digital art.",
            order = 2,
        ),
        AiStylePreset(
            key = "sketch",
            displayNameRes = Res.string.ai_style_sketch_name,
            descriptionRes = Res.string.ai_style_sketch_description,
            auditName = "Pencil Sketch",
            prompt = "Transform this into a detailed graphite pencil sketch. Monochrome, hand-drawn texture, cross-hatching shading, on textured paper background.",
            order = 3,
        ),
        AiStylePreset(
            key = "clay",
            displayNameRes = Res.string.ai_style_clay_name,
            descriptionRes = Res.string.ai_style_clay_description,
            auditName = "Clay Animation",
            prompt = "Transform this into a stop-motion clay animation style. Plasticine texture, fingerprints visible on clay, warm studio lighting, playful 3D render.",
            order = 4,
        ),
        AiStylePreset(
            key = "pixar",
            displayNameRes = Res.string.ai_style_pixar_name,
            descriptionRes = Res.string.ai_style_pixar_description,
            auditName = "Pixar 3D",
            prompt = "Transform this into 3D animated cartoon style rendered like a Pixar movie, soft rounded shapes, cheerful, expressive, warm lighting, friendly atmosphere, high quality 3D render, cute.",
            order = 5,
        ),
        AiStylePreset(
            key = "oilpainting",
            displayNameRes = Res.string.ai_style_oilpainting_name,
            descriptionRes = Res.string.ai_style_oilpainting_description,
            auditName = "Impressionist Oil Painting",
            prompt = "Transform this into oil painting in the style of Claude Monet impressionism, visible thick brushstrokes, vibrant light, soft color palette, dreamy atmosphere, artistic masterpiece on canvas.",
            order = 6,
        ),
        AiStylePreset(
            key = "artcomic",
            displayNameRes = Res.string.ai_style_artcomic_name,
            descriptionRes = Res.string.ai_style_artcomic_description,
            auditName = "Pop Art Comic",
            prompt = "Transform this into retro pop art comic book panel style, bold black outlines, vibrant primary colors, halftone dot pattern (Ben-Day dots), energetic, vintage comic aesthetic.",
            order = 7,
        ),
    ).sortedBy(AiStylePreset::order)

    fun styles(): List<AiStylePreset> = presets
}

 data class AiStylePreset(val key: String, val displayNameRes: StringResource, val descriptionRes: StringResource, val auditName: String, val prompt: String, val order: Int)
