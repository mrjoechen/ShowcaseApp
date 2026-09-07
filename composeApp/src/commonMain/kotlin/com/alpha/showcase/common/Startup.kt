package com.alpha.showcase.common

import com.alpha.showcase.common.security.initializeConfigEncryption
import com.alpha.showcase.common.ui.settings.SettingPreferenceRepo
import com.alpha.showcase.common.utils.Analytics
import com.alpha.showcase.common.utils.AnonymousUsageController
import com.alpha.showcase.common.utils.Supabase
import com.alpha.showcase.common.ai.AiServices
import isWeb
import getPlatform
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Startup {
	private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	fun run(antilog: Antilog = DebugAntilog()): Result<Unit> {
		Napier.base(antilog)
		val encryptionFailure = runCatching { initializeConfigEncryption() }.exceptionOrNull()
		if (encryptionFailure != null) {
			return Result.failure(encryptionFailure)
		}
		getPlatform().init()
		if (!isWeb()) {
			val ai = AiServices.engine
			ai.scope.launch {
				try { ai.initialize() }
				catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
				catch (_: Exception) { Napier.w("AI task recovery could not complete") }
			}
		}
		Analytics.initialize(anonymousUsage = false)
		Supabase.clearLegacyBrowserSession()
		startupScope.launch {
			Supabase.warmUp()
			val hasAnonymousUsageConsent = runCatching {
				SettingPreferenceRepo().getPreference().hasAnonymousUsageConsent
			}.getOrDefault(false)
			AnonymousUsageController.applyConsent(hasAnonymousUsageConsent)
		}
//		runBlocking {
//			println("Hello, World!")
//
//
//			// 服务器端
//			launch {
//				// 启动UDP监听
//				launch {
//					DeviceDiscovery.listenForBroadcasts { clientAddress ->
//						println("Discovered client at: $clientAddress")
//						launch {
//							DeviceDiscovery.sendResponse(clientAddress)
//						}
//					}
//				}
//
//				launch {
//					DeviceDiscovery.broadcastPresence()
//				}
//
//				// 启动TCP服务器
//				TcpCommunication.startServer { clientSocket ->
//					println("Client connected via TCP: ${clientSocket.remoteAddress}")
//					val receivedData = clientSocket.receiveData()
//					println("Received data: $receivedData")
////					clientSocket.sendData("Server received: $receivedData ${Clock.System.now()}")
////					clientSocket.close()
//				}
//
//
//			}
//		}
		return Result.success(Unit)
	}
}
