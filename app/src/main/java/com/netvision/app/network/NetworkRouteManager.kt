package com.netvision.app.network

import android.content.Context
import android.net.ConnectivityManager
import java.net.NetworkInterface
import java.util.Collections

data class NetworkInterfaceInfo(
    val name: String,
    val displayName: String,
    val ipAddress: String?,
    val isVpn: Boolean,
    val isUp: Boolean,
    val mtu: Int
)

class NetworkRouteManager(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun getActiveInterfaces(): List<NetworkInterfaceInfo> {
        val result = mutableListOf<NetworkInterfaceInfo>()
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue

                val addresses = Collections.list(intf.inetAddresses)
                val ipv4 = addresses.firstOrNull { !it.isLoopbackAddress && it.hostAddress.indexOf(':') < 0 }?.hostAddress

                val isVpn = intf.name.contains("tun") || intf.name.contains("ppp") || intf.name.contains("tap")

                result.add(
                    NetworkInterfaceInfo(
                        name = intf.name,
                        displayName = intf.displayName,
                        ipAddress = ipv4,
                        isVpn = isVpn,
                        isUp = intf.isUp,
                        mtu = intf.mtu
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    fun getAccessibleSubnets(): List<String> {
        val subnets = mutableListOf<String>()
        val interfaces = getActiveInterfaces()
        for (intf in interfaces) {
            intf.ipAddress?.let { ip ->
                val parts = ip.split(".")
                if (parts.size == 4) {
                    subnets.add("${parts[0]}.${parts[1]}.${parts[2]}.0/24")
                }
            }
        }
        return subnets.distinct()
    }
}
