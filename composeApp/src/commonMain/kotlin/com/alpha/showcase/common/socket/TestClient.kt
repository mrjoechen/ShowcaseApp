package com.alpha.showcase.common.socket

import com.alpha.showcase.common.socket.TcpCommunication.receiveData
import com.alpha.showcase.common.socket.TcpCommunication.sendData
import io.ktor.network.sockets.InetSocketAddress
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock

fun launchClient() {
    runBlocking {
        println("Hello, World!")

        // 客户端
        launch {
            var serverAddress: InetSocketAddress? = null
            val job = launch {
                DeviceDiscovery.listenForBroadcasts { address ->
                    println("Discovered server at: $address")
                    serverAddress = address
                    this.cancel() // 停止监听
                }
            }

            // 等待发现服务器或超时
            withTimeout(1000000) {
                job.join()
            }

            serverAddress?.let { address ->
                val socket = TcpCommunication.connectToServer(address)
                socket.sendData("Hello from client! ${Clock.System.now()}")
                val response = socket.receiveData()
                println("Server response: $response")
                socket.close()
            } ?: println("No server found")
        }
    }

}