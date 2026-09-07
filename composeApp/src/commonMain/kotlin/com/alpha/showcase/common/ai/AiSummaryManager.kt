package com.alpha.showcase.common.ai

import coil3.Image
import com.alpha.ai.imagegeneration.*
import com.alpha.facedetection.FaceInspectionResult
import com.alpha.facedetection.FaceInspector
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.ui.play.ResolvedImageModel
import com.alpha.showcase.common.ui.play.UrlWithAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.uuid.Uuid

/** Requests are deduplicated in the application scope, including while a page is off screen. */
internal class AiSummaryManager(private val engine: AiEngine, inspectorFactory: () -> FaceInspector) {
    private class Entry {
        var raw = AiSummaryState()
        var inspection: FaceInspectionResult? = null
        val inspectionMutex = Mutex()
        var inspectionJob: Job? = null
        val visible = MutableStateFlow(AiSummaryState())
    }
    private val inspector by lazy(inspectorFactory)
    private val states = mutableMapOf<String, Entry>()
    private val jobs = mutableMapOf<String, Job>()
    private val requests = mutableMapOf<String, AiSummaryRequest>()
    private val execution = Semaphore(1)

    /** Every source supplies displayed pixels; source URLs/paths stay in local cache identity only. */
    suspend fun prepare(key: String, image: Image, profile: AiProfile?, language: String): AiSummaryRequest {
        engine.initialize()
        val maxInputBytes = if (profile == null) 512L * 1024 else
            (engine.client.providerDescriptor(ProviderId(profile.providerId)) ?: error("Missing provider")).capabilities.maxInputBytes
        // Re-encoding strips source metadata and freezes a small image before queuing provider work.
        val encoded = encodeAiImage(image, maxBytes = minOf(maxInputBytes, 512L * 1024), maxEdge = 768)
        val contentKey = "$key:${encoded.bytes.toByteString().sha256().hex()}:${image.shareable}".encodeUtf8().sha256().hex()
        return AiSummaryRequest(contentKey, encoded, profile, language, privacyEligible = image.shareable)
    }

    fun observe(request: AiSummaryRequest): StateFlow<AiSummaryState> =
        entry(request.key).visible

    private fun entry(key: String): Entry = states.getOrPut(key) { Entry().also(::publish) }

    private fun publish(entry: Entry) {
        entry.visible.value = if (!engine.library.value.facePrivacyEnabled) entry.raw else when (entry.inspection) {
            null -> AiSummaryState(facePrivacyPending = true)
            FaceInspectionResult.FACE_DETECTED -> AiSummaryState(facePrivacyBlocked = true)
            FaceInspectionResult.INDETERMINATE -> AiSummaryState(facePrivacyUnavailable = true)
            FaceInspectionResult.NO_FACE -> entry.raw
        }
    }

    /** Called synchronously with persisted setting changes so cached text disappears immediately. */
    fun refreshPrivacy() {
        states.values.forEach(::publish)
        if (engine.library.value.facePrivacyEnabled) {
            requests.toMap().forEach { (key, request) ->
                states[key]?.let { entry ->
                    if (entry.inspection == null && entry.inspectionJob?.isActive != true) {
                        // A pending network call must not delay local protection when toggled on.
                        entry.inspectionJob = engine.scope.launch {
                            if (!privacyAllows(request, entry)) jobs[key]?.cancel()
                        }
                    } else if (entry.inspection == FaceInspectionResult.FACE_DETECTED ||
                        entry.inspection == FaceInspectionResult.INDETERMINATE) {
                        jobs[key]?.cancel()
                    }
                }
            }
        }
    }

    private fun update(entry: Entry, state: AiSummaryState) { entry.raw = state; publish(entry) }

    private suspend fun privacyAllows(request: AiSummaryRequest, entry: Entry): Boolean = entry.inspectionMutex.withLock {
        if (!engine.library.value.facePrivacyEnabled) return@withLock true
        if (entry.inspection == null || entry.inspection == FaceInspectionResult.INDETERMINATE) {
            entry.inspection = null
            publish(entry)
            entry.inspection = if (!request.privacyEligible) FaceInspectionResult.INDETERMINATE else try {
                inspector.inspect(request.image.bytes)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Native linkage/allocation failures must never turn into permission to upload.
                FaceInspectionResult.INDETERMINATE
            }
            publish(entry)
        }
        !engine.library.value.facePrivacyEnabled || entry.inspection == FaceInspectionResult.NO_FACE
    }

    fun request(request: AiSummaryRequest, force: Boolean = false) {
        val key = request.key
        val profile = request.profile
        val language = request.language
        if (jobs[key]?.isActive == true) return
        val state = entry(key)
        update(state, AiSummaryState(generating = profile != null))
        val job = engine.scope.launch(start = CoroutineStart.LAZY) {
            try {
                engine.initialize()
                // Inspect independently of the remote queue, including images with no AI profile.
                if (!privacyAllows(request, state)) return@launch
                if (profile == null) { update(state, AiSummaryState()); return@launch }
                execution.withPermit {
                    if (!privacyAllows(request, state)) return@withPermit
                    val encoded = request.image
                    // Observation, in-flight deduplication and persistence all use the same pixels.
                    val cached = engine.library.value.summaries[key]
                    if (!force && cached != null) {
                        update(state, AiSummaryState(content = cached))
                        return@withPermit
                    }
                    val template = checkNotNull(DefaultImageUnderstandingTemplateCatalog.template("slide-summary"))
                    var content: AiSummaryContent? = null
                    engine.withCredential(profile) { token ->
                        // Credential access can suspend while protection is switched on.
                        if (!privacyAllows(request, state)) return@withCredential
                        currentCoroutineContext().ensureActive()
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
                    if (!privacyAllows(request, state)) return@withPermit
                    val output = content ?: error("Invalid summary response")
                    engine.saveSummary(key, output)
                    if (!privacyAllows(request, state)) return@withPermit
                    update(state, AiSummaryState(content = output))
                }
            } catch (cancelled: CancellationException) {
                update(state, state.raw.copy(generating = false))
                throw cancelled
            } catch (_: Exception) {
                update(state, state.raw.copy(generating = false, failed = true))
            } finally {
                jobs.remove(key)
                requests.remove(key)
                while (states.size > 256) {
                    val unused = states.entries.firstOrNull {
                        it.value.visible.subscriptionCount.value == 0 && it.key !in jobs && it.value.inspectionJob?.isActive != true
                    } ?: break
                    states.remove(unused.key)
                }
            }
        }
        jobs[key] = job
        requests[key] = request
        job.start()
    }
}

internal class AiSummaryRequest internal constructor(
    val key: String,
    val image: EncodedAiImage,
    val profile: AiProfile?,
    val language: String,
    val privacyEligible: Boolean = true,
)

internal fun aiSummaryKey(media: Any, profile: AiProfile?, language: String): String {
    val mediaIdentity = when (media) {
        is NetworkFile -> listOf(media.key, media.modTime, media.size.toString()).joinToString("\u0000")
        is DataWithType -> aiSummaryKey(media.data, profile, language)
        is UrlWithAuth -> media.url
        is ResolvedImageModel -> media.cacheKey
        else -> media.toString()
    }
    // Discard summaries produced when bitmap thumbnails could contain only a cropped corner.
    return listOf(mediaIdentity, profile?.id.orEmpty(), profile?.revision.toString(), language, "slide-summary-v2", "full-frame-v1")
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
