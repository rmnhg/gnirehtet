package com.genymobile.gnirehtet.mydadb

import com.cgutman.adblib.AdbCrypto
import com.genymobile.gnirehtet.myadb.UsbChannel
import dadb.Dadb
import java.lang.AutoCloseable

class UsbDadb (
    private val adbCrypto: AdbCrypto,
    private val usbChannel: UsbChannel
) : Dadb {

    private var connection: AdbConnection? = null

    override fun open(destination: String) = connection().open(destination) as dadb.AdbStream

    override fun supportsFeature(feature: String) = connection().supportsFeature(feature)

    override fun close() {
        connection?.close()
    }

    override fun toString() = "usb-device"

    @Synchronized
    private fun connection(): AdbConnection {
        var connection = connection
        if (connection == null) {
            connection = newConnection()
            this.connection = connection
        }
        return connection
    }

    private fun newConnection(): AdbConnection {
        val adbConnection = AdbConnection.connect(
                AdbReader(usbChannel),
                AdbWriter(usbChannel),
                adbCrypto,
                usbChannel
            )
        return adbConnection
    }

    fun forward(hostPort: Int, targetPort: Int): AutoCloseable {
        val forwarder = TcpForwarder(this, "tcp:$hostPort", "tcp:$targetPort")
        forwarder.start()

        return forwarder
    }

    fun forward(hostPort: Int, targetAbstract: String): AutoCloseable {
        val forwarder = TcpForwarder(this, "tcp:$hostPort", "localabstract:$targetAbstract")
        forwarder.start()

        return forwarder
    }

    fun forward(hostAbstract: String, targetPort: Int): AutoCloseable {
        val forwarder = TcpForwarder(this, "localabstract:$hostAbstract", "tcp:$targetPort")
        forwarder.start()

        return forwarder
    }

    fun forward(hostAbstract: String, targetAbstract: String): AutoCloseable {
        val forwarder = TcpForwarder(this, "localabstract:$hostAbstract", "localabstract:$targetAbstract")
        forwarder.start()

        return forwarder
    }
    fun reverse(hostPort: Int, targetPort: Int): AutoCloseable {
        val reverser = TcpReverser(this, "tcp:$hostPort", "tcp:$targetPort")
        reverser.start()

        return reverser
    }

    fun reverse(hostPort: Int, targetAbstract: String): AutoCloseable {
        val reverser = TcpReverser(this, "tcp:$hostPort", "localabstract:$targetAbstract")
        reverser.start()

        return reverser
    }

    fun reverse(hostAbstract: String, targetPort: Int): AutoCloseable {
        val reverser = TcpReverser(this, "localabstract:$hostAbstract", "tcp:$targetPort")
        reverser.start()

        return reverser
    }

    fun reverse(hostAbstract: String, targetAbstract: String): AutoCloseable {
        val reverser = TcpReverser(this, "localabstract:$hostAbstract", "localabstract:$targetAbstract")
        reverser.start()

        return reverser
    }
}