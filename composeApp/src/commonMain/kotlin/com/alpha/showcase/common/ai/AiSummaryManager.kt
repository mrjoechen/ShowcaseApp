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

    /** Source address and AI configuration never participate in a verified file's identity. */
    suspend fun prepare(media: Any, image: Image, profile: AiProfile?, language: String,
        identity: ImageContentIdentity? = null): AiSummaryRequest {
        engine.initialize()
        // Re-encoding strips source metadata and freezes a small upload before queuing work.
        // Its digest is used only to recognize legacy JSON records, never as a file hash.
        val encoded = encodeAiImage(image, maxBytes = 512L * 1024, maxEdge = 768)
        val pixels = encoded.bytes.toByteString().sha256().hex()
        fun contentKey(key: String) = "$key:$pixels:${image.shareable}".encodeUtf8().sha256().hex()
        val oldKey = contentKey(aiSummaryKey(media, language))
        val key = identity?.contentId ?: "unverified:$oldKey"
        val library = engine.library.value
        // Old keys are hashes and cannot be decoded. Reconstruct them using retained
        // profile revisions, including archived ones, only when the new key is absent.
        val legacyKeys = if (library.summaries.isNotEmpty()) {
            ((library.profiles + listOfNotNull(profile)).map {
                contentKey(legacyAiSummaryKey(media, it, language))
            } + oldKey).filter { it in library.summaries }.toSet()
        } else emptySet()
        return AiSummaryRequest(key, encoded, profile, language, privacyEligible = image.shareable, legacyKeys = legacyKeys,
            identity = identity, reference = identity?.let { summaryFileReference(media, it) })
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
        update(state, AiSummaryState(content = state.raw.content, generating = profile != null && request.identity != null))
        val job = engine.scope.launch(start = CoroutineStart.LAZY) {
            try {
                engine.initialize()
                // Inspect independently of the remote queue, including images with no AI profile.
                if (!privacyAllows(request, state)) return@launch
                execution.withPermit {
                    if (!privacyAllows(request, state)) return@withPermit
                    val encoded = request.image
                    val identity = request.identity
                    // No verified file bytes means no new paid request or false file-hash record.
                    if (identity == null) { update(state, AiSummaryState(failed = profile != null)); return@withPermit }
                    val library = engine.library.value
                    val legacy = request.legacyKeys.associateWith { oldKey ->
                        library.summaryHistory[oldKey].orEmpty() + listOfNotNull(library.summaries[oldKey])
                    }
                    engine.summaryRepository.migrate(identity, legacy, language)
                    val cached = engine.summaryRepository.find(identity)
                    if ((!force || profile == null) && cached != null) {
                        request.reference?.let { engine.summaryRepository.rememberReference(it) }
                        update(state, AiSummaryState(content = cached))
                        return@withPermit
                    }
                    if (profile == null) { update(state, AiSummaryState()); return@withPermit }
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
                    engine.summaryRepository.save(identity, output, language)
                    request.reference?.let { engine.summaryRepository.rememberReference(it) }
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
                // Bound only unobserved presentation state; persisted summaries are never evicted.
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
    val legacyKeys: Set<String> = emptySet(),
    val identity: ImageContentIdentity? = null,
    val reference: SummaryFileReference? = null,
)

internal fun aiSummaryKey(media: Any, language: String): String = summaryMediaKey(media, language, null)

/** Read compatibility only; new summaries never include AI configuration in their identity. */
private fun legacyAiSummaryKey(media: Any, profile: AiProfile, language: String): String =
    summaryMediaKey(media, language, profile)

private fun summaryMediaKey(media: Any, language: String, legacyProfile: AiProfile?): String {
    val mediaIdentity = when (media) {
        is NetworkFile -> listOf(media.key, media.modTime, media.size.toString()).joinToString("\u0000")
        is DataWithType -> summaryMediaKey(media.data, language, legacyProfile)
        is UrlWithAuth -> media.url
        is ResolvedImageModel -> media.cacheKey
        else -> media.toString()
    }
    // Avoid reusing results produced when bitmap thumbnails contained only a cropped corner.
    val profileIdentity = legacyProfile?.let { listOf(it.id, it.revision.toString()) }.orEmpty()
    return (listOf(mediaIdentity) + profileIdentity + listOf(language, "slide-summary-v2", "full-frame-v1"))
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
