package com.genymobile.gnirehtet.mydadb

/*
 * Copyright (c) 2021 mobile.dev inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import com.genymobile.gnirehtet.myadb.UsbChannel
import com.genymobile.gnirehtet.myadb.AdbMessage as origAdbMsg
import okio.Source
import okio.buffer
import dadb.AdbStream
import android.util.Log

class AdbReader(private val sink: UsbChannel) : AutoCloseable {


    fun readMessage(): AdbMessage {
        //synchronized(sink) {
            sink.apply {
                val adbMsg = origAdbMsg.parseAdbMessage(sink)
                val command = adbMsg.command
                val arg0 = adbMsg.arg0
                val arg1 = adbMsg.arg1
                val payloadLength = adbMsg.payloadLength
                val checksum = adbMsg.checksum
                val magic = adbMsg.magic
                val payload = if (adbMsg.payloadLength > 0) adbMsg.payload else byteArrayOf()
                return AdbMessage(command, arg0, arg1, payloadLength, checksum, magic, payload).also {
                    //log { "(${Thread.currentThread().name}) < $it" }
                    //Log.d("Gnirehtet", "readMessage: $it");
                }
            }
        //}
    }

    override fun close() {
        sink.close()
    }

    private val ENABLED = "true" == System.getenv("DADB_LOGGING")

    internal fun log(block: () -> String) {
        if (ENABLED) {
            println(block())
        }
    }
}
