package com.alpha.showcase.common.socket

import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers

object TcpCommunication {
    suspend fun startServer(onClientConnected: suspend (Socket) -> Unit) {
        val serverSocket = aSocket(SelectorManager(Dispatchers.Default)).tcp()
            .bind(InetSocketAddress("0.0.0.0", Constants.SHARED_PORT))
        while (true) {
            val accept = serverSocket.accept()
            onClientConnected(accept)
        }
    }

    suspend fun connectToServer(address: InetSocketAddress): Socket {
        return aSocket(SelectorManager(Dispatchers.Default)).tcp().connect(address)
    }

    suspend fun Socket.sendData(data: String) {
        openWriteChannel(autoFlush = true).writeStringUtf8("$data\n")
    }

    suspend fun Socket.receiveData(): String {
        return openReadChannel().readUTF8Line() ?: ""
    }
}
