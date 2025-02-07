package com.genymobile.gnirehtet.mydadb

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Log
import java.lang.AutoCloseable

import dadb.Dadb
import okio.BufferedSink
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.BufferUnderflowException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.concurrent.thread

internal class TcpForwarder(
    private val dadb: Dadb,
    private val host: String,
    private val target: String,
) : AutoCloseable {

    private var state: State = State.STOPPED
    private var serverThread: Thread? = null
    private var server: AutoCloseable? = null
    private var clientExecutor: ExecutorService? = null
    private val ENABLED = "true" == System.getenv("DADB_LOGGING")
    private val TAG = "Gnirehtet TCPForwarder"

    internal fun log(block: () -> String) {
        if (ENABLED) {
            println(block())
        }
    }

    fun start() {
        check(state == State.STOPPED) { "Forwarder is already started at $host" }

        moveToState(State.STARTING)

        clientExecutor = Executors.newCachedThreadPool()
        serverThread = thread {
            try {
                handleForwarding()
            } catch (ignored: SocketException) {
                // Do nothing
            } catch (e: IOException) {
                log { "could not start TCP port forwarding: ${e.message}" }
            } finally {
                moveToState(State.STOPPED)
            }
        }

        waitFor(10, 5000) {
            state == State.STARTED
        }
    }

    private fun handleForwarding() {
        val serverRef = if ("tcp:" in host)
            ServerSocket(host.substringAfter("tcp:").toInt())
        else
            LocalServerSocket(host.substringAfter("localabstract:"))
        server = serverRef

        var tcpServer: ServerSocket? = null
        var abstractServer: LocalServerSocket? = null

        if ("tcp:" in host) {
            tcpServer = serverRef as ServerSocket
        } else {
            abstractServer = serverRef as LocalServerSocket
        }

        moveToState(State.STARTED)

        while (!Thread.interrupted()) {
            val client = if ("tcp:" in host) tcpServer!!.accept() else abstractServer!!.accept()

            clientExecutor?.execute {
                val adbStream = dadb.open(target)
                Log.d(TAG, "New client detected. Invoking dadb.open to forward.")

                val readerThread = thread {
                    val inputSteam: InputStream = if ("tcp:" in host)
                            (client as Socket).getInputStream()
                    else
                            (client as LocalSocket).inputStream
                    forward(
                        inputSteam.source(),
                        adbStream.sink,
                        writer = false
                    )
                }

                try {
                    val outputStreamSink = if ("tcp:" in host)
                            (client as Socket).sink()
                    else
                            (client as LocalSocket).outputStream.sink()
                    forward(
                        adbStream.source,
                        outputStreamSink.buffer(),
                        writer = true
                    )
                } finally {
                    try {
                        adbStream.close()
                        client?.close()
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to close client", e)
                    }

                    readerThread.interrupt()
                }
            }
        }
    }

    override fun close() {
        if (state == State.STOPPED || state == State.STOPPING) {
            return
        }

        // Make sure that we are not stopping the server while it is in a transient state
        // to avoid surprises
        waitFor(10, 5000) {
            state == State.STARTED
        }

        moveToState(State.STOPPING)

        server?.close()
        server = null
        serverThread?.interrupt()
        serverThread = null
        clientExecutor?.shutdown()
        clientExecutor?.awaitTermination(5, TimeUnit.SECONDS)
        clientExecutor = null

        waitFor(10, 5000) {
            state == State.STOPPED
        }
    }

    private fun forward(source: Source, sink: BufferedSink, writer: Boolean) {
        try {
            while (!Thread.interrupted()) {
                try {
                    if (source.read(sink.buffer, 256) >= 0) {
                        sink.flush()
                        /*if (writer) {
                            //Thread.sleep(500)
                            Log.d(
                                TAG,
                                "Readed 256 bytes on reader!"
                            )
                        } else {
                            Log.d(
                                TAG,
                                "Readed 256 bytes on writer!"
                            )
                        }*/
                    } else {
                        Log.e(TAG, "Stopped reading from ${if (writer) "writer" else "reader"}! Stopping!")
                        return
                    }
                } catch (e: IOException) {
                    // Do nothing
                    Log.d(TAG, "Ignored IOException on ${if (writer) "writer" else "reader"}:", e)
                    Thread.sleep(10)
                } catch (e: BufferUnderflowException) {
                    Log.w(TAG, "Buffer Underflow on ${if (writer) "writer" else "reader"} detected. Retrying after 100 ms...")
                    Thread.sleep(100)
                    continue
                }
            }
        } catch (e: InterruptedException) {
            // Do nothing
            Log.d(TAG, "Ignored InterruptedException on ${if (writer) "writer" else "reader"}:", e)
        } catch (e: InterruptedIOException) {
            // do nothing
            Log.d(TAG, "Ignored InterruptedException on ${if (writer) "writer" else "reader"}:", e)
        }
    }

    private fun moveToState(state: State) {
        this.state = state
    }

    private enum class State {
        STARTING,
        STARTED,
        STOPPING,
        STOPPED
    }

    private fun waitFor(intervalMs: Int, timeoutMs: Int, test: () -> Boolean) {
        val start = System.currentTimeMillis()
        var lastCheck = start
        while (!test()) {
            val now = System.currentTimeMillis()
            val timeSinceStart = now - start
            val timeSinceLastCheck = now - lastCheck
            if (timeoutMs in 0..timeSinceStart) {
                throw TimeoutException()
            }
            val sleepTime = intervalMs - timeSinceLastCheck
            if (sleepTime > 0) {
                Thread.sleep(sleepTime)
            }
            lastCheck = System.currentTimeMillis()
        }
    }

}
