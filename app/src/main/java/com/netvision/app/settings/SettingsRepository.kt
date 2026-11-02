package com.netvision.app.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.netvision.app.monitoring.SnmpDeviceInfo
import com.netvision.app.monitoring.SnmpV2Client

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("netvision_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun getSettings(): AppSettings = _settings.value

    fun updateSnmpCommunities(values: List<String>) {
        val safeList = values.map { it.trim() }.filter { it.isNotEmpty() }.distinct().ifEmpty { listOf("public") }
        val current = getSettings()
        updateSettings(current.copy(snmpCommunities = safeList, snmpCommunity = safeList.first()))
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("snmp_device_community_") }.forEach { key ->
            val saved = prefs.getString(key, null)
            if (saved != null && saved !in safeList) editor.remove(key)
        }
        editor.apply()
    }

    fun getCommunitiesForDevice(ip: String): List<String> {
        val configured = getSettings().snmpCommunities.ifEmpty { listOf(getSettings().snmpCommunity.ifBlank { "public" }) }
        val saved = prefs.getString("snmp_device_community_$ip", null)
        // Mimosa radios commonly ship with the read community "mimosa".
        // Keep the user's configured communities first, then try the vendor default.
        return (listOfNotNull(saved?.takeIf { it in configured }) + configured.filter { it != saved } + "mimosa").distinct()
    }

    suspend fun queryDeviceInfo(ip: String, port: Int, preferredVersion: String? = null, timeoutMs: Int = 1000): Result<SnmpDeviceInfo> {
        var lastFailure: Result<SnmpDeviceInfo>? = null
        for (community in getCommunitiesForDevice(ip)) {
            val result = SnmpV2Client.queryDeviceInfo(ip, community, port, preferredVersion, timeoutMs)
            if (result.isSuccess && result.getOrNull()?.isSuccess == true) {
                prefs.edit().putString("snmp_device_community_$ip", community).apply()
                return result
            }
            lastFailure = result
        }
        return lastFailure ?: Result.failure(IllegalStateException("No SNMP community configured"))
    }

    fun updateSettings(newSettings: AppSettings) {
        prefs.edit().apply {
            putString("language", newSettings.language.code)
            putString("theme_mode", newSettings.themeMode.name)
            putString("default_subnet", newSettings.defaultSubnet)
            putInt("scan_timeout_ms", newSettings.scanTimeoutMs)
            putBoolean("enable_ubnt", newSettings.enableUbiquitiDiscovery)
            putBoolean("enable_mndp", newSettings.enableMikroTikMndp)
            putBoolean("enable_snmp", newSettings.enableSnmpPolling)
            putString("snmp_community", newSettings.snmpCommunity)
            putString("snmp_communities", newSettings.snmpCommunities.joinToString("\n"))
            putString("snmp_version", newSettings.snmpVersion)
            putInt("snmp_port", newSettings.snmpPort)
            putInt("refresh_interval_sec", newSettings.monitoringRefreshIntervalSec)
            apply()
        }
        _settings.value = newSettings
    }

    private fun loadSettings(): AppSettings {
        val langCode = prefs.getString("language", AppLanguage.ARABIC.code) ?: AppLanguage.ARABIC.code
        val lang = if (langCode == "en") AppLanguage.ENGLISH else AppLanguage.ARABIC
        val themeName = prefs.getString("theme_mode", AppThemeMode.DARK.name) ?: AppThemeMode.DARK.name
        val theme = try { AppThemeMode.valueOf(themeName) } catch (e: Exception) { AppThemeMode.DARK }

        return AppSettings(
            language = lang,
            themeMode = theme,
            defaultSubnet = prefs.getString("default_subnet", "192.168.1.0/24") ?: "192.168.1.0/24",
            scanTimeoutMs = prefs.getInt("scan_timeout_ms", 1200),
            enableUbiquitiDiscovery = prefs.getBoolean("enable_ubnt", true),
            enableMikroTikMndp = prefs.getBoolean("enable_mndp", true),
            enableSnmpPolling = prefs.getBoolean("enable_snmp", true),
            snmpCommunity = prefs.getString("snmp_community", "public") ?: "public",
            snmpCommunities = prefs.getString("snmp_communities", null)?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: listOf(prefs.getString("snmp_community", "public") ?: "public"),
            snmpVersion = prefs.getString("snmp_version", "v2c") ?: "v2c",
            snmpPort = prefs.getInt("snmp_port", 161),
            monitoringRefreshIntervalSec = prefs.getInt("refresh_interval_sec", 5)
        )
    }
}
