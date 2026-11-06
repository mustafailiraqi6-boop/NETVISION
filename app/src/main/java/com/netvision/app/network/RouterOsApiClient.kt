package com.netvision.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal RouterOS API client for authenticated read-only system resource queries.
 * Uses the standard RouterOS API sentence framing over TCP.
 */
class RouterOsApiClient(
    private val connectTimeoutMs: Int = 2500,
    private val readTimeoutMs: Int = 3000
) {
    suspend fun fetchSystemResources(
        host: String,
        port: Int,
        username: String,
        password: String
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            require(port in 1..65535) { "Invalid RouterOS API port" }
            require(username.isNotBlank()) { "RouterOS API username is empty" }
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.soTimeout = readTimeoutMs
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())

                writeSentence(output, listOf("/login", "=name=$username", "=password=$password"))
                val login = readReply(input)
                if (login.any { it.firstOrNull() == "!trap" } || login.none { it.firstOrNull() == "!done" }) {
                    error(login.firstOrNull { it.firstOrNull() == "!trap" }?.joinToString(" ") ?: "RouterOS API login failed")
                }

                writeSentence(output, listOf("/system/resource/print"))
                val reply = readReply(input)
                val trap = reply.firstOrNull { it.firstOrNull() == "!trap" }
                if (trap != null) error(trap.joinToString(" "))
                val record = reply.firstOrNull { it.firstOrNull() == "!re" }
                    ?: error("RouterOS did not return system resource data")
                record.drop(1).mapNotNull { word ->
                    if (!word.startsWith("=")) null
                    else {
                        val split = word.indexOf('=', 1)
                        if (split < 0) null else word.substring(1, split) to word.substring(split + 1)
                    }
                }.toMap()
            }
        }
    }

    private fun writeSentence(output: BufferedOutputStream, words: List<String>) {
        words.forEach { word ->
            val bytes = word.toByteArray(Charsets.UTF_8)
            writeLength(output, bytes.size)
            output.write(bytes)
        }
        output.write(0)
        output.flush()
    }

    private fun readReply(input: BufferedInputStream): List<List<String>> {
        val sentences = mutableListOf<List<String>>()
        while (true) {
            val words = mutableListOf<String>()
            while (true) {
                val length = readLength(input)
                if (length == 0) break
                require(length <= 1024 * 1024) { "RouterOS API response word is too large" }
                val bytes = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val count = input.read(bytes, offset, length - offset)
                    if (count < 0) error("RouterOS API connection closed unexpectedly")
                    offset += count
                }
                words += String(bytes, Charsets.UTF_8)
            }
            if (words.isEmpty()) continue
            sentences += words
            if (words.first() == "!done") return sentences
            if (words.first() == "!fatal") error(words.joinToString(" "))
        }
    }

    private fun readLength(input: BufferedInputStream): Int {
        val first = input.read()
        if (first < 0) error("RouterOS API connection closed")
        return when {
            first < 0x80 -> first
            first < 0xC0 -> ((first and 0x3F) shl 8) or readByte(input)
            first < 0xE0 -> ((first and 0x1F) shl 16) or (readByte(input) shl 8) or readByte(input)
            first < 0xF0 -> ((first and 0x0F) shl 24) or (readByte(input) shl 16) or (readByte(input) shl 8) or readByte(input)
            else -> {
                var value = 0
                repeat(4) { value = (value shl 8) or readByte(input) }
                value
            }
        }
    }

    private fun readByte(input: BufferedInputStream): Int {
        val value = input.read()
        if (value < 0) error("RouterOS API connection closed while reading length")
        return value
    }

    private fun writeLength(output: BufferedOutputStream, length: Int) {
        when {
            length < 0x80 -> output.write(length)
            length < 0x4000 -> {
                output.write((length shr 8) or 0x80)
                output.write(length and 0xFF)
            }
            length < 0x200000 -> {
                output.write((length shr 16) or 0xC0)
                output.write((length shr 8) and 0xFF)
                output.write(length and 0xFF)
            }
            length < 0x10000000 -> {
                output.write((length shr 24) or 0xE0)
                output.write((length shr 16) and 0xFF)
                output.write((length shr 8) and 0xFF)
                output.write(length and 0xFF)
            }
            else -> {
                output.write(0xF0)
                output.write((length shr 24) and 0xFF)
                output.write((length shr 16) and 0xFF)
                output.write((length shr 8) and 0xFF)
                output.write(length and 0xFF)
            }
        }
    }
}
