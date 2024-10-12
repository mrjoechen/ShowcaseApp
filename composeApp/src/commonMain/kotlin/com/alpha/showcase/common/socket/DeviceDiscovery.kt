package com.alpha.showcase.common.socket

import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.*
import kotlinx.datetime.Clock

object DeviceDiscovery {
    suspend fun broadcastPresence() {
        val socket = aSocket(SelectorManager(Dispatchers.Default)).udp().bind {
            broadcast = true
        }

        while (true) {
            delay(Constants.BROADCAST_INTERVAL)

            val ipMsgSend = IpMessageProtocol().apply {
                version = "1"
                senderName = "TestServer"
                cmd = 0x00000001 // Online Command
                additionalSection = Clock.System.now().toEpochMilliseconds().toString()
            }
            val packet = ByteReadPacket(ipMsgSend.protocolString.toByteArray())
            socket.send(Datagram(packet, InetSocketAddress("255.255.255.255", Constants.SHARED_PORT)))
        }
    }

    suspend fun sendResponse(clientAddress: InetSocketAddress) {
        val socket = aSocket(SelectorManager(Dispatchers.Default)).udp().bind()
        val ipMsgSend = IpMessageProtocol().apply {
            version = "1"
            senderName = "TestServer"
            cmd = 0x00000003 // Online Response Command
            additionalSection = Clock.System.now().toEpochMilliseconds().toString()
        }
        val packet = ByteReadPacket(ipMsgSend.protocolString.toByteArray())
        socket.send(Datagram(packet, clientAddress))
    }

    suspend fun listenForBroadcasts(onDeviceDiscovered: (InetSocketAddress) -> Unit) {
        val socket = aSocket(SelectorManager(Dispatchers.Default)).udp()
            .bind(InetSocketAddress("0.0.0.0", Constants.SHARED_PORT))

        while (true) {
            val datagram = socket.receive()
            val message = datagram.packet.readText()
            println(message + " from " + datagram.address + " at " + Clock.System.now())
            if (message.startsWith(Constants.DISCOVERY_MESSAGE)) {
                onDeviceDiscovered(datagram.address as InetSocketAddress)
            }
        }
    }
}