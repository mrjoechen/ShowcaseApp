package com.alpha.showcase.common

import com.alpha.showcase.common.security.initializeConfigEncryption
import com.alpha.showcase.common.socket.DeviceDiscovery
import com.alpha.showcase.common.socket.TcpCommunication
import com.alpha.showcase.common.socket.TcpCommunication.receiveData
import com.alpha.showcase.common.socket.TcpCommunication.sendData
import com.alpha.showcase.common.utils.Analytics
import com.alpha.showcase.common.utils.SupabaseAuth
import getPlatform
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

object Startup {
	fun run() {
		initializeConfigEncryption()
		Napier.base(DebugAntilog())
		SupabaseAuth.initialize()
		Analytics.initialize()
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
