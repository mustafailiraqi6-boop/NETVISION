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

            // Some airOS builds return a login page with HTTP 200 instead of a redirect.
            // Treat a non-JSON response as an expired/missing session and authenticate once.
            var response = getStats(base, cookie)
            var counters = parseWlanCounters(response.second)
            if (response.first !in 200..299 || counters == null) {
                cookie = login(base, username, password, cookie)
                cookiesByDevice[ipAddress] = cookie
                response = getStats(base, cookie)
                counters = parseWlanCounters(response.second)
            }

            check(response.first in 200..299) { "airOS HTTP ${response.first}" }
            counters ?: error("airOS WLAN0 counters missing or response was not valid JSON")
        }

    private fun parseWlanCounters(body: String): UbiquitiTrafficCounters? = runCatching {
        val json = JSONObject(body)
        val interfaces = json.optJSONArray("interfaces") ?: return null
        for (i in 0 until interfaces.length()) {
            val item = interfaces.optJSONObject(i) ?: continue
            if (!item.optString("ifname").equals("ath0", ignoreCase = true)) continue
            val stats = item.optJSONObject("stats") ?: continue
            val rx = stats.optString("rx_bytes").toLongOrNull() ?: continue
            val tx = stats.optString("tx_bytes").toLongOrNull() ?: continue
            return UbiquitiTrafficCounters(rxBytes = rx, txBytes = tx)
        }
        null
    }.getOrNull()

    private fun getStats(base: String, cookie: String?): Pair<Int, String> {
        val connection = (URL("$base/ifstats.cgi?").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json, */*")
            setRequestProperty("Connection", "close")
            cookie?.let { setRequestProperty("Cookie", it) }
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val responseCookies = extractCookies(connection)
            if (responseCookies.isNotBlank()) {
                cookiesByDevice[base.removePrefix("http://")] = responseCookies
            }
            code to body
        } finally {
            connection.disconnect()
        }
    }

    private fun login(base: String, username: String, password: String, existingCookie: String?): String {
        val boundary = "----NetVision${UUID.randomUUID().toString().replace("-", "")}"
        val connection = (URL("$base/login.cgi").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 5000
            readTimeout = 5000
            doOutput = true
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Connection", "close")
            existingCookie?.let { setRequestProperty("Cookie", it) }
        }
        val fields = linkedMapOf(
            "uri" to "/ifstats.cgi",
            "username" to username,
            "password" to password,
            "submit" to "Login"
        )
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
            val responseCookies = extractCookies(connection)
            val merged = mergeCookies(existingCookie, responseCookies)
            check(code in 200..399 && merged.isNotBlank()) {
                "airOS login failed (HTTP $code)"
            }
            cookiesByDevice[base.removePrefix("http://")] = merged
            return merged
        } finally {
            connection.disconnect()
        }
    }

    private fun extractCookies(connection: HttpURLConnection): String =
        connection.headerFields.entries
            .filter { it.key.equals("Set-Cookie", ignoreCase = true) }
            .flatMap { it.value.orEmpty() }
            .mapNotNull { header -> header.substringBefore(";").trim().takeIf { it.contains("=") } }
            .distinct()
            .joinToString("; ")

    private fun mergeCookies(existing: String?, received: String): String {
        val values = linkedMapOf<String, String>()
        existing.orEmpty().split(";").map(String::trim).filter(String::isNotEmpty).forEach { cookie ->
            val name = cookie.substringBefore("=").trim()
            if (name.isNotEmpty()) values[name] = cookie.substringAfter("=", "").let { "$name=$it" }
        }
        received.split(";").map(String::trim).filter(String::isNotEmpty).forEach { cookie ->
            val name = cookie.substringBefore("=").trim()
            if (name.isNotEmpty()) values[name] = cookie.substringAfter("=", "").let { "$name=$it" }
        }
        return values.values.joinToString("; ")
    }
}

/** Calculated live throughput for the airOS WLAN0 interface. */
data class UbiquitiTrafficSnapshot(
    val txMbps: Double,
    val rxMbps: Double,
    val sampledAt: Long = System.currentTimeMillis()
)
