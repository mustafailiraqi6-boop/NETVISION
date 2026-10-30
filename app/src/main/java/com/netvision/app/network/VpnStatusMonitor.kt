package com.netvision.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Real VPN Status Monitor utilizing Android ConnectivityManager and NetworkCallback.
 * Monitors active TRANSPORT_VPN networks independently of client apps (e.g., OpenVPN Connect).
 */
data class VpnState(
    val isConnected: Boolean = false,
    val interfaceName: String? = null,
    val localIpAddress: String? = null,
    val activeRoutes: List<String> = emptyList(),
    val lastCheckTimestamp: Long = System.currentTimeMillis()
)

class VpnStatusMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _vpnState = MutableStateFlow(VpnState())
    val vpnState: StateFlow<VpnState> = _vpnState.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateVpnStatus(network)
            }

            override fun onLost(network: Network) {
                _vpnState.value = VpnState(
                    isConnected = false,
                    interfaceName = null,
                    localIpAddress = null,
                    activeRoutes = emptyList(),
                    lastCheckTimestamp = System.currentTimeMillis()
                )
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                updateVpnStatus(network)
            }
        }

        connectivityManager.registerNetworkCallback(request, networkCallback!!)
        checkCurrentVpnState()
    }

    fun stopMonitoring() {
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
            networkCallback = null
        }
    }

    fun checkCurrentVpnState() {
        val activeNetworks = connectivityManager.allNetworks
        var foundVpn = false

        for (network in activeNetworks) {
            val caps = connectivityManager.getNetworkCapabilities(network)
            if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                updateVpnStatus(network)
                foundVpn = true
                break
            }
        }

        if (!foundVpn) {
            _vpnState.value = VpnState(
                isConnected = false,
                lastCheckTimestamp = System.currentTimeMillis()
            )
        }
    }

    private fun updateVpnStatus(network: Network) {
        val linkProps = connectivityManager.getLinkProperties(network)
        val interfaceName = linkProps?.interfaceName ?: "tun0"
        val ipAddress = linkProps?.linkAddresses?.firstOrNull()?.address?.hostAddress
        val routes = linkProps?.routes?.map { it.toString() } ?: emptyList()

        _vpnState.value = VpnState(
            isConnected = true,
            interfaceName = interfaceName,
            localIpAddress = ipAddress,
            activeRoutes = routes,
            lastCheckTimestamp = System.currentTimeMillis()
        )
    }
}
