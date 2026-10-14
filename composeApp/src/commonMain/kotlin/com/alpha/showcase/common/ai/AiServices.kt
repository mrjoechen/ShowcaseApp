package com.alpha.showcase.common.ai

import com.alpha.ai.imagegeneration.AiModel
import com.alpha.ai.imagegeneration.provider.registerBuiltIns
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.storage.objectStoreOf
import getPlatform
import isWeb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App-owned work outlives individual playback pages and dialogs. Never initialized by web UI. */
internal object AiServices {
    val engine: AiEngine by lazy {
        check(aiFeaturesAvailable(isWeb())) { "AI is available only in installed clients" }
        AiEngine(
            store = createAiLibraryStore(),
            files = createAiFiles(),
            client = AiModel.builder().registerBuiltIns().build(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            encrypt = RConfig::encryptAsync,
            decrypt = RConfig::decryptAsync,
            workAvailable = ::scheduleAiBackgroundWork,
            // Device-local and never synced: each device asks before its first upload to a host.
            uploadConsent = AiUploadConsent(
                required = { getPlatform().requiresAiUploadConsent },
                store = objectStoreOf("ai_upload_consent"),
            ),
        )
    }
}
