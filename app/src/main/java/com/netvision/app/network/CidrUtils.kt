package com.netvision.app.network

data class SubnetDetails(
    val cidr: String,
    val networkAddress: String,
    val netmask: String,
    val broadcastAddress: String,
    val firstUsableIp: String,
    val lastUsableIp: String,
    val totalHosts: Int
)

object CidrUtils {

    /**
     * Parses an IPv4 CIDR string (e.g. 192.168.1.0/24, 10.197.56.224/27).
     * Returns true if string is a valid IPv4 CIDR or plain IPv4 address.
     */
    fun isValidCidr(cidrStr: String): Boolean {
        val trimmed = cidrStr.trim()
        val parts = trimmed.split("/")
        if (parts.size == 1) {
            return isValidIpv4(parts[0])
        }
        if (parts.size == 2) {
            val prefix = parts[1].toIntOrNull() ?: return false
            return isValidIpv4(parts[0]) && prefix in 8..32
        }
        return false
    }

    fun isValidIpv4(ip: String): Boolean {
        val octets = ip.split(".")
        if (octets.size != 4) return false
        return octets.all { octet ->
            val num = octet.toIntOrNull() ?: return@all false
            num in 0..255 && (octet == "0" || !octet.startsWith("0"))
        }
    }

    private fun ipToLong(ip: String): Long {
        val octets = ip.split(".").map { it.toLong() }
        return (octets[0] shl 24) or (octets[1] shl 16) or (octets[2] shl 8) or octets[3]
    }

    private fun longToIp(value: Long): String {
        return "${(value shr 24) and 0xFF}.${(value shr 16) and 0xFF}.${(value shr 8) and 0xFF}.${value and 0xFF}"
    }

    /**
     * Generates a list of all usable host IP addresses strictly within the specified CIDR.
     * Guaranteed to match the user's subnet boundary (e.g., 10.197.56.224/27 -> 10.197.56.225 to 10.197.56.254).
     */
    fun getUsableHostIps(cidrStr: String, maxHosts: Int = 1024): List<String> {
        val trimmed = cidrStr.trim()
        val parts = trimmed.split("/")
        val ip = parts[0]
        val prefix = if (parts.size == 2) parts[1].toIntOrNull() ?: 24 else 32

        if (prefix == 32) {
            return listOf(ip)
        }

        val ipLong = ipToLong(ip)
        val mask = (-1L shl (32 - prefix)) and 0xFFFFFFFFL
        val networkLong = ipLong and mask
        val broadcastLong = networkLong or (mask.inv() and 0xFFFFFFFFL)

        if (prefix >= 31) {
            // /31 subnets in RFC 3021 have 2 usable addresses without network/broadcast distinction
            return listOf(longToIp(networkLong), longToIp(broadcastLong))
        }

        val firstUsable = networkLong + 1
        val lastUsable = broadcastLong - 1
        val count = (lastUsable - firstUsable + 1).coerceAtLeast(0).toInt()
        val limitedCount = count.coerceAtMost(maxHosts)

        val result = ArrayList<String>(limitedCount)
        for (i in 0 until limitedCount) {
            result.add(longToIp(firstUsable + i))
        }
        return result
    }

    fun getSubnetDetails(cidrStr: String): SubnetDetails? {
        if (!isValidCidr(cidrStr)) return null
        val trimmed = cidrStr.trim()
        val parts = trimmed.split("/")
        val ip = parts[0]
        val prefix = if (parts.size == 2) parts[1].toIntOrNull() ?: 24 else 32

        val ipLong = ipToLong(ip)
        val mask = (-1L shl (32 - prefix)) and 0xFFFFFFFFL
        val networkLong = ipLong and mask
        val broadcastLong = networkLong or (mask.inv() and 0xFFFFFFFFL)

        val firstUsable = if (prefix >= 31) networkLong else networkLong + 1
        val lastUsable = if (prefix >= 31) broadcastLong else broadcastLong - 1
        val totalHosts = if (prefix >= 31) 2 else (lastUsable - firstUsable + 1).coerceAtLeast(0).toInt()

        return SubnetDetails(
            cidr = "$ip/$prefix",
            networkAddress = longToIp(networkLong),
            netmask = longToIp(mask),
            broadcastAddress = longToIp(broadcastLong),
            firstUsableIp = longToIp(firstUsable),
            lastUsableIp = longToIp(lastUsable),
            totalHosts = totalHosts
        )
    }
}
