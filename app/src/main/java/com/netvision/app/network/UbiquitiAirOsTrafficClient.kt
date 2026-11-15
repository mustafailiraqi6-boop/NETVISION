package com.netvision.app.network

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class UbiquitiTrafficCounters(
    val rxBytes: Long,
    val txBytes: Long,
    val sampledAt: Long = System.currentTimeMillis()
)

/**
 * Reads live WLAN0 byte counters from airOS ifstats.cgi.
 * Each device keeps its own session cookies; credentials are never logged.
 */
class UbiquitiAirOsTrafficClient {
    private val cookiesByDevice = ConcurrentHashMap<String, String>()

    fun readWlanCounters(ipAddress: String, username: String, password: String): Result<UbiquitiTrafficCounters> =
        runCatching {
            require(ipAddress.isNotBlank() && username.isNotBlank() && password.isNotBlank())
            val base = "http://$ipAddress"
            var cookie = cookiesByDevice[ipAddress]
            var response = getStats(base, cookie)
            if (response.first == 302 || response.first == 401 || response.first == 403 || response.first !in 200..299) {
                cookie = login(base, username, password, cookie)
                cookiesByDevice[ipAddress] = cookie
                response = getStats(base, cookie)
            }
            check(response.first in 200..299) { "airOS HTTP ${response.first}" }
            val json = JSONObject(response.second)
            val interfaces = json.optJSONArray("interfaces") ?: error("airOS interfaces missing")
            var rx: Long? = null
            var tx: Long? = null
            for (i in 0 until interfaces.length()) {
                val item = interfaces.optJSONObject(i) ?: continue
                if (!item.optString("ifname").equals("ath0", ignoreCase = true)) continue
                val stats = item.optJSONObject("stats") ?: continue
                rx = stats.optString("rx_bytes").toLongOrNull()
                tx = stats.optString("tx_bytes").toLongOrNull()
                break
            }
            UbiquitiTrafficCounters(rx ?: error("WLAN0 RX counter unavailable"), tx ?: error("WLAN0 TX counter unavailable"))
        }

    private fun getStats(base: String, cookie: String?): Pair<Int, String> {
        val connection = (URL("$base/ifstats.cgi?").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3500
            readTimeout = 3500
            instanceFollowRedirects = false
            cookie?.let { setRequestProperty("Cookie", it) }
        }
        return try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val setCookie = connection.getHeaderField("Set-Cookie")
            if (!setCookie.isNullOrBlank()) cookiesByDevice[base] = setCookie.substringBefore(";")
            code to body
        } finally { connection.disconnect() }
    }

    private fun login(base: String, username: String, password: String, existingCookie: String?): String {
        val boundary = "----NetVision${UUID.randomUUID().toString().replace("-", "")}"
        val connection = (URL("$base/login.cgi").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 3500
            readTimeout = 3500
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            existingCookie?.let { setRequestProperty("Cookie", it) }
        }
        val fields = linkedMapOf("uri" to "/ifstats.cgi", "username" to username, "password" to password, "submit" to "Login", "lang_changed" to "0")
        try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                fields.forEach { (name, value) ->
                    writer.append("--$boundary\r\n")
                    writer.append("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    writer.append(value).append("\r\n")
                }
                writer.append("--$boundary--\r\n")
            }
            val code = connection.responseCode
            val cookie = connection.getHeaderField("Set-Cookie")?.substringBefore(";")
            val existing = existingCookie.orEmpty()
            val merged = listOfNotNull(existing.takeIf { it.isNotBlank() }, cookie?.takeIf { it.isNotBlank() }).distinct().joinToString("; ")
            check(merged.isNotBlank() && (code in 200..399)) { "airOS login failed (HTTP $code)" }
            return merged
        } finally { connection.disconnect() }
    }
}
