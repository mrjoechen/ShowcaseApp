package com.alpha.showcase.common

import com.alpha.showcase.common.security.initializeConfigEncryption
import com.alpha.showcase.common.ui.settings.SettingPreferenceRepo
import com.alpha.showcase.common.utils.Analytics
import com.alpha.showcase.common.utils.SupabaseAuth
import getPlatform
import initializeSentry
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object Startup {
	private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	fun run(): Result<Unit> {
		Napier.base(DebugAntilog())
		val encryptionFailure = runCatching { initializeConfigEncryption() }.exceptionOrNull()
		if (encryptionFailure != null) {
			return Result.failure(encryptionFailure)
		}
		getPlatform().init()
		Analytics.initialize(anonymousUsage = false)
		SupabaseAuth.initialize()
		startupScope.launch {
			val anonymousUsage = runCatching {
				SettingPreferenceRepo().getPreference().anonymousUsage
			}.getOrDefault(false)
			Analytics.getInstance().setAnonymousUsage(anonymousUsage)
			if (anonymousUsage) {
				initializeSentry()
			}
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
