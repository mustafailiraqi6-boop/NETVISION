package com.netvision.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.netvision.app.monitoring.MikrotikStationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

/**
 * Minimal RouterOS API client for reading wireless registration-table rows.
 * It uses the active Android VPN network when one is present.
 */
class RouterOsApiClient(private val context: Context) {

    suspend fun readWirelessRegistrationTable(
        ipAddress: String,
        username: String,
        password: String,
        port: Int
    ): Result<List<MikrotikStationInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            require(port in 1..65535) { "Invalid RouterOS API port" }
            require(username.isNotBlank() && password.isNotBlank()) { "API credentials are missing" }

            Socket().use { socket ->
                socket.soTimeout = SOCKET_TIMEOUT_MS
                findVpnNetwork()?.bindSocket(socket)
                socket.connect(InetSocketAddress(ipAddress, port), CONNECT_TIMEOUT_MS)

                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())

                sendSentence(output, listOf("/login", "=name=$username", "=password=$password"))
                val loginReply = readReply(input)
                if (loginReply.trap != null) throw IllegalStateException(loginReply.trap)
                if (!loginReply.done) throw IllegalStateException("RouterOS API login did not complete")

                val modern = runCommand(input, output, "/interface/wireless/registration-table/print")
                val rows = if (modern.trap == null && modern.records.isNotEmpty()) {
                    modern.records
                } else {
                    val wifi = runCommand(input, output, "/interface/wifi/registration-table/print")
                    if (wifi.trap != null) throw IllegalStateException(modern.trap ?: wifi.trap)
                    wifi.records
                }

                rows.mapNotNull { row -> row.toStationInfo() }
            }
        }
    }

    private fun findVpnNetwork() =
        (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
            .allNetworks.firstOrNull { network ->
                (context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                    .getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }

    private data class Reply(
        val records: List<Map<String, String>>,
        val done: Boolean,
        val trap: String? = null
    )

    private fun runCommand(
        input: BufferedInputStream,
        output: BufferedOutputStream,
        command: String
    ): Reply {
        sendSentence(output, listOf(command))
        return readReply(input)
    }

    private fun readReply(input: BufferedInputStream): Reply {
        val records = mutableListOf<Map<String, String>>()
        while (true) {
            val sentence = readSentence(input)
            if (sentence.isEmpty()) continue
            when (sentence.first()) {
                "!re" -> {
                    val values = linkedMapOf<String, String>()
                    sentence.drop(1).forEach { word ->
                        if (word.startsWith("=")) {
                            val separator = word.indexOf('=', 1)
                            if (separator > 1) {
                                values[word.substring(1, separator)] = word.substring(separator + 1)
                            }
                        }
                    }
                    records += values
                }
                "!trap" -> {
                    val message = sentence.firstOrNull { it.startsWith("=message=") }
                        ?.substringAfter("=message=")
                        ?: "RouterOS API command failed"
                    return Reply(records, done = false, trap = message)
                }
                "!done" -> return Reply(records, done = true)
            }
        }
    }

    private fun sendSentence(output: BufferedOutputStream, words: List<String>) {
        words.forEach { word ->
            val bytes = word.toByteArray(Charsets.UTF_8)
            writeLength(output, bytes.size)
            output.write(bytes)
        }
        output.write(0)
        output.flush()
    }

    private fun readSentence(input: BufferedInputStream): List<String> {
        val words = mutableListOf<String>()
        while (true) {
            val length = readLength(input)
            if (length == 0) return words
            require(length in 1..MAX_WORD_LENGTH) { "Invalid RouterOS API word length" }
            val bytes = ByteArray(length)
            var offset = 0
            while (offset < length) {
                val count = input.read(bytes, offset, length - offset)
                if (count < 0) throw IllegalStateException("RouterOS API connection closed")
                offset += count
            }
            words += String(bytes, Charsets.UTF_8)
        }
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

    private fun readLength(input: BufferedInputStream): Int {
        val first = input.read()
        if (first < 0) throw IllegalStateException("RouterOS API connection closed")
        return when {
            (first and 0x80) == 0 -> first
            (first and 0xC0) == 0x80 -> ((first and 0x3F) shl 8) or readRequiredByte(input)
            (first and 0xE0) == 0xC0 -> ((first and 0x1F) shl 16) or
                (readRequiredByte(input) shl 8) or readRequiredByte(input)
            (first and 0xF0) == 0xE0 -> ((first and 0x0F) shl 24) or
                (readRequiredByte(input) shl 16) or (readRequiredByte(input) shl 8) or readRequiredByte(input)
            (first and 0xF8) == 0xF0 -> (readRequiredByte(input) shl 24) or
                (readRequiredByte(input) shl 16) or (readRequiredByte(input) shl 8) or readRequiredByte(input)
            else -> throw IllegalStateException("Invalid RouterOS API length prefix")
        }
    }

    private fun readRequiredByte(input: BufferedInputStream): Int {
        val value = input.read()
        if (value < 0) throw IllegalStateException("RouterOS API connection closed")
        return value
    }

    private fun Map<String, String>.toStationInfo(): MikrotikStationInfo? {
        val mac = this["mac-address"]?.uppercase(Locale.ROOT)
            ?.takeIf { Regex("^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$").matches(it) }
            ?: return null

        fun number(key: String): Int? = this[key]?.substringBefore('@')?.trim()?.toIntOrNull()
        fun rate(key: String): Double? {
            val raw = this[key]?.trim() ?: return null
            val value = Regex("""^([0-9]+(?:\.[0-9]+)?)""").find(raw)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                ?: return null
            return value
        }

        val signalRx = number("signal-strength")
        val signalTx = number("tx-signal-strength")
        return MikrotikStationInfo(
            macAddress = mac,
            deviceName = this["radio-name"]?.takeIf { it.isNotBlank() },
            signalDbm = signalRx ?: signalTx,
            txRateMbps = rate("tx-rate"),
            rxRateMbps = rate("rx-rate"),
            connectionTime = this["uptime"]?.takeIf { it.isNotBlank() },
            txSignalDbm = signalTx,
            rxSignalDbm = signalRx,
            txCcqPercent = number("tx-ccq"),
            rxCcqPercent = number("rx-ccq"),
            lastActivity = this["last-activity"]?.takeIf { it.isNotBlank() }
        )
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 4000
        private const val SOCKET_TIMEOUT_MS = 5000
        private const val MAX_WORD_LENGTH = 1_048_576
    }
}
