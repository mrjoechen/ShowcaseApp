package com.alpha.showcase.common

import com.alpha.showcase.common.security.initializeConfigEncryption
import com.alpha.showcase.common.socket.DeviceDiscovery
import com.alpha.showcase.common.socket.TcpCommunication
import com.alpha.showcase.common.socket.TcpCommunication.receiveData
import com.alpha.showcase.common.socket.TcpCommunication.sendData
import com.alpha.showcase.common.ui.settings.SettingPreferenceRepo
import com.alpha.showcase.common.utils.Analytics
import com.alpha.showcase.common.utils.SupabaseAuth
import getPlatform
import initializeSentry
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking

object Startup {
	fun run() {
		initializeConfigEncryption()
		Napier.base(DebugAntilog())
		val anonymousUsage = runBlocking {
			SettingPreferenceRepo().getPreference().anonymousUsage
		}
		if (anonymousUsage) {
			initializeSentry()
		}
		SupabaseAuth.initialize()
		Analytics.initialize(anonymousUsage)
        getPlatform().init()
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
	}
}
