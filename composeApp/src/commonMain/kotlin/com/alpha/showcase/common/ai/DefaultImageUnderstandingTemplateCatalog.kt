package com.alpha.showcase.common.ai

import com.alpha.ai.imagegeneration.ImageUnderstandingSchema
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/** Product defaults used until a remote template adapter is selected. */
object DefaultImageUnderstandingTemplateCatalog : ImageUnderstandingTemplateCatalog {
    const val SLIDE_SUMMARY_ID = "slide-summary"
    const val GENERAL_ANALYSIS_ID = "general-analysis"
    const val CLASSIFICATION_ID = "classification"
    const val COPYWRITING_ID = "copywriting"

    private val CONTENT_TYPES = listOf(
        "photo",
        "illustration",
        "screenshot",
        "document",
        "graphic_design",
        "other",
    )

    private val CATEGORIES = listOf(
        "people",
        "animal",
        "food",
        "landscape",
        "architecture",
        "transportation",
        "product",
        "document",
        "screenshot",
        "artwork",
        "event",
        "other",
    )

    private val COPY_TONES = listOf(
        "neutral",
        "warm",
        "playful",
        "professional",
        "inspirational",
    )

    private val presets = listOf(
        ImageUnderstandingTemplate(
            id = SLIDE_SUMMARY_ID,
            version = 2,
            name = "播放摘要、旁白与分类标签",
            description = "为幻灯片播放生成简短摘要、一句画外旁白和最多五个可检索的分类标签。",
            prompt = """
                Analyze the supplied image using only clearly visible evidence.
                Write one concise, factual summary in the requested output language. Prefer at most 64 Chinese characters for Chinese or 40 words for other languages.
                Generate between one and five distinct classification tags in the requested output language. Each tag must be concise and useful for grouping photos later.
                Choose the most informative tags across these dimensions when visibly supported: main subject, scene, time or lighting, place or environment, visual style, activity, and festival or event.
                Examples of the desired granularity include 风景, 夜晚, 街拍, 城市, 节日, 人像, 建筑, 美食, 海边, 雪景, 宠物, and 演出.
                Do not use # prefixes. Do not repeat near-synonyms. Do not guess identities, exact locations, events, or sensitive traits.

                Also write one short narration line for an electronic photo frame. Its purpose is not to describe the image again, but to add a small implication: the sentence that remains in mind after seeing it.
                Narration principles:
                1. Base any association only on information established by the image. Never invent a time, relationship, or event background.
                2. Make it natural and interesting, with restrained humor or poetry, but avoid sentimentality, motivational slogans, elementary-school composition, and formulaic expressions.
                3. It may express a subtle everyday mood, mild self-deprecation or dry humor, an understated thought about memory or an instant, or a plain observation with an aftertaste.
                4. Avoid clichés equivalent to 世界、梦、时光、岁月、温柔、治愈、刚刚好、悄悄、慢慢 unless genuinely necessary.
                5. Avoid constructions equivalent to “……里……着整个世界/整个夏天”, simple “……得像……” similes, “……比……还……”, and “……得比……更……”.
                6. Do not refer to the medium with phrases equivalent to “这张照片”, “这一刻”, or “那天”.
                7. Return exactly one sentence with no newline, quotation marks, prefix, or explanation. For Chinese, target 8–24 Han characters and never exceed 30; for other languages, keep it similarly brief, preferably 4–18 words.
            """.trimIndent(),
            responseSchema = ImageUnderstandingSchema.objectSchema(
                name = "showcase_slide_summary_v2",
                properties = linkedMapOf(
                    "summary" to stringSchema("基于可见内容、使用请求所指定语言的客观摘要；中文建议不超过 64 个汉字，其他语言建议不超过 40 个单词。"),
                    "narration" to stringSchema("使用请求所指定语言，生成一句电子相框画外旁白；不复述画面，要根据图片内容生成一句充满诗意，有趣，或能引发情绪共鸣的一句话，中文建议 8～24 个汉字且不超过 30 个汉字，其他语言保持同等简短。"),
                    "tags" to stringArraySchema(
                        description = "一到五个互不重复、使用请求所指定语言的分类标签。",
                        minItems = 1,
                        maxItems = 5,
                    ),
                ),
            ),
            order = 1,
        ),
        ImageUnderstandingTemplate(
            id = GENERAL_ANALYSIS_ID,
            version = 1,
            name = "综合理解与摘要",
            description = "识别画面主体、场景、可见文字和关键词，并生成客观摘要。",
            prompt = """
                Analyze the supplied image using only evidence that is visibly present.
                Describe the main subjects, their actions or relationships, the scene, and important visual details.
                Extract readable text exactly as it appears; do not infer text that is obscured or unreadable.
                Do not guess identities, locations, brands, events, or intent when the image does not establish them.
                Write all descriptive output in Simplified Chinese, while preserving extracted visible text in its original language.
                If a field has no supported value, return an empty string or empty array as appropriate. Never fabricate missing details.
            """.trimIndent(),
            responseSchema = ImageUnderstandingSchema.objectSchema(
                name = "showcase_general_analysis_v1",
                properties = linkedMapOf(
                    "title" to stringSchema("简洁、客观的图片标题，不超过 40 个汉字。"),
                    "summary" to stringSchema("一到两句话概括图片的核心内容，不超过 150 个汉字。"),
                    "detailedDescription" to stringSchema("按视觉层次描述主体、动作、环境与重要细节，不超过 600 个汉字。"),
                    "subjects" to stringArraySchema("画面中明确可见的主要主体。", maxItems = 12),
                    "scene" to stringSchema("图片发生的环境或场景；无法判断时返回空字符串，不超过 120 个汉字。"),
                    "visibleText" to stringArraySchema("按阅读顺序提取出的可见文字，保留原语言。", maxItems = 30),
                    "keywords" to stringArraySchema("便于检索图片的中文关键词。", maxItems = 15),
                ),
            ),
            order = 2,
        ),
        ImageUnderstandingTemplate(
            id = CLASSIFICATION_ID,
            version = 1,
            name = "分类与标签",
            description = "生成稳定的内容类型、主分类、辅助分类、标签和置信度。",
            prompt = """
                Classify the supplied image from visible evidence only.
                Choose exactly one contentType and one primaryCategory from the values allowed by the response schema.
                secondaryCategories must contain only distinct allowed category values and must not repeat primaryCategory.
                Generate concise Simplified Chinese tags for visible subjects, scene, activity, style, and notable attributes.
                confidence must reflect certainty in the primary classification, from 0.0 to 1.0.
                Keep rationale short, factual, and in Simplified Chinese. Do not identify unknown people or infer sensitive traits.
            """.trimIndent(),
            responseSchema = ImageUnderstandingSchema.objectSchema(
                name = "showcase_classification_v1",
                properties = linkedMapOf(
                    "contentType" to enumStringSchema(
                        description = "图片媒介类型。",
                        values = CONTENT_TYPES,
                    ),
                    "primaryCategory" to enumStringSchema(
                        description = "最能代表图片内容的主分类。",
                        values = CATEGORIES,
                    ),
                    "secondaryCategories" to enumStringArraySchema(
                        description = "可选辅助分类，不得与主分类重复。",
                        values = CATEGORIES,
                        maxItems = 3,
                    ),
                    "tags" to stringArraySchema("描述可见内容的中文检索标签。", maxItems = 12),
                    "confidence" to numberSchema("主分类置信度，范围为 0 到 1。", minimum = 0, maximum = 1),
                    "rationale" to stringSchema("主分类依据的简短客观说明，不超过 120 个汉字。"),
                ),
            ),
            order = 3,
        ),
        ImageUnderstandingTemplate(
            id = COPYWRITING_ID,
            version = 1,
            name = "图片文案",
            description = "生成标题、说明文案、无障碍替代文本、标签和可选行动语。",
            prompt = """
                Create reusable copy grounded strictly in the visible content of the supplied image.
                Write natural Simplified Chinese. Preserve proper nouns only when they are clearly readable or visually established.
                The headline should be concise; the caption may be vivid but must not invent facts, identities, endorsements, or outcomes.
                altText must objectively convey the information needed by someone who cannot see the image and must not start with phrases such as “图片中” or “一张图片”.
                Hashtags must be distinct; each one must begin with # and contain no spaces. Select the closest allowed tone.
                Add a callToAction only when it naturally fits the image; otherwise return an empty string.
            """.trimIndent(),
            responseSchema = ImageUnderstandingSchema.objectSchema(
                name = "showcase_copywriting_v1",
                properties = linkedMapOf(
                    "headline" to stringSchema("简洁的中文标题，不超过 40 个汉字。"),
                    "caption" to stringSchema("忠于画面、可直接使用的中文说明文案，不超过 250 个汉字。"),
                    "altText" to stringSchema("客观完整的中文无障碍替代文本，不超过 150 个汉字。"),
                    "hashtags" to stringArraySchema(
                        description = "互不重复、以 # 开头且不含空格的相关标签。",
                        maxItems = 10,
                    ),
                    "tone" to enumStringSchema(
                        description = "文案的主要语气。",
                        values = COPY_TONES,
                    ),
                    "callToAction" to stringSchema("适合画面时使用的简短行动语，否则为空字符串；不超过 60 个汉字。"),
                ),
            ),
            order = 4,
        ),
    ).sortedBy(ImageUnderstandingTemplate::order)

    override fun templates(): List<ImageUnderstandingTemplate> = presets

    private fun stringSchema(description: String): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("string"))
        put("description", JsonPrimitive(description))
    }

    private fun enumStringSchema(
        description: String,
        values: List<String>,
    ): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("string"))
        put("description", JsonPrimitive(description))
        put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    }

    private fun stringArraySchema(
        description: String,
        minItems: Int = 0,
        maxItems: Int,
    ): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("array"))
        put("description", JsonPrimitive(description))
        put("minItems", JsonPrimitive(minItems))
        put("maxItems", JsonPrimitive(maxItems))
        put("items", buildJsonObject {
            put("type", JsonPrimitive("string"))
        })
    }

    private fun enumStringArraySchema(
        description: String,
        values: List<String>,
        maxItems: Int,
    ): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("array"))
        put("description", JsonPrimitive(description))
        put("maxItems", JsonPrimitive(maxItems))
        put("items", buildJsonObject {
            put("type", JsonPrimitive("string"))
            put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
        })
    }

    private fun numberSchema(
        description: String,
        minimum: Int,
        maximum: Int,
    ): JsonElement = buildJsonObject {
        put("type", JsonPrimitive("number"))
        put("description", JsonPrimitive(description))
        put("minimum", JsonPrimitive(minimum))
        put("maximum", JsonPrimitive(maximum))
    }
}
