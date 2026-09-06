package com.alpha.showcase.common.ai

import coil3.Image
import com.alpha.ai.imagegeneration.*
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.ui.play.UrlWithAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.uuid.Uuid

/** Requests are deduplicated in the application scope, including while a page is off screen. */
internal class AiSummaryManager(private val engine: AiEngine) {
    private val states = mutableMapOf<String, MutableStateFlow<AiSummaryState>>()
    private val jobs = mutableMapOf<String, Job>()
    private val execution = Semaphore(1)

    fun observe(key: String): StateFlow<AiSummaryState> = states.getOrPut(key) { MutableStateFlow(AiSummaryState()) }

    fun request(key: String, image: Image, profile: AiProfile, language: String, force: Boolean = false) {
        if (jobs[key]?.isActive == true) return
        val state = states.getOrPut(key) { MutableStateFlow(AiSummaryState()) }
        state.value = state.value.copy(generating = true, failed = false)
        val job = engine.scope.launch(start = CoroutineStart.LAZY) {
            try {
                execution.withPermit {
                    engine.initialize()
                    val descriptor = engine.client.providerDescriptor(ProviderId(profile.providerId)) ?: error("Missing provider")
                    val encoded = encodeAiImage(image, descriptor.capabilities.maxInputBytes)
                    // Include actual displayed bytes: replacing a photo at the same URL cannot reuse stale text.
                    val cacheKey = "$key:${encoded.bytes.toByteString().sha256().hex()}".encodeUtf8().sha256().hex()
                    val cached = engine.library.value.summaries[cacheKey]
                    if (!force && cached != null) {
                        state.value = AiSummaryState(content = cached)
                        return@withPermit
                    }
                    val template = checkNotNull(DefaultImageUnderstandingTemplateCatalog.template("slide-summary"))
                    var content: AiSummaryContent? = null
                    engine.withCredential(profile) { token ->
                        engine.client.understandImage(template.createRequest(
                            operationId = OperationId(Uuid.random().toString()),
                            source = ByteArrayImageSource(encoded.bytes, encoded.mimeType),
                            outputLanguage = ImageUnderstandingOutputLanguage(language),
                        ), profile.runtimeConfig(token)).collect { event ->
                            if (event is ImageUnderstandingEvent.Completed) {
                                val result = event.result
                                if (result is ImageUnderstandingResult.Success) content = parseAiSummary(result.output.data, language)
                                else error("Image understanding failed")
                            }
                        }
                    }
                    val output = content ?: error("Invalid summary response")
                    engine.saveSummary(cacheKey, output)
                    state.value = AiSummaryState(content = output)
                }
            } catch (cancelled: CancellationException) {
                state.value = state.value.copy(generating = false)
                throw cancelled
            } catch (_: Exception) {
                state.value = state.value.copy(generating = false, failed = true)
            } finally {
                jobs.remove(key)
                while (states.size > 256) {
                    val unused = states.entries.firstOrNull { it.value.subscriptionCount.value == 0 && it.key !in jobs } ?: break
                    states.remove(unused.key)
                }
            }
        }
        jobs[key] = job
        job.start()
    }
}

internal fun aiSummaryKey(media: Any, profile: AiProfile, language: String): String {
    val mediaIdentity = when (media) {
        is NetworkFile -> listOf(media.key, media.modTime, media.size.toString()).joinToString("\u0000")
        is DataWithType -> aiSummaryKey(media.data, profile, language)
        is UrlWithAuth -> media.url
        else -> media.toString()
    }
    return listOf(mediaIdentity, profile.id, profile.revision.toString(), language, "slide-summary-v2")
        .joinToString("\u0000").encodeUtf8().sha256().hex()
}

internal fun parseAiSummary(data: JsonObject, language: String): AiSummaryContent? {
    fun text(name: String) = (data[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val chinese = language.startsWith("zh", ignoreCase = true)
    val summary = text("summary")?.trim()?.take(if (chinese) 64 else 300)?.takeIf { it.isNotBlank() } ?: return null
    val narration = text("narration")?.replace(Regex("\\s+"), " ")?.trim()?.trim('"', '\'', '“', '”', '「', '」')
        ?.take(if (chinese) 30 else 160)?.takeIf { it.isNotBlank() } ?: return null
    val tags = (data["tags"] as? JsonArray)?.mapNotNull {
        (it as? JsonPrimitive)?.takeIf { it.isString }?.content
    }?.map { it.trim().removePrefix("#").take(24) }?.filter { it.isNotBlank() }?.distinct()?.take(5).orEmpty()
    if (tags.isEmpty()) return null
    return AiSummaryContent(summary, narration, tags)
}
