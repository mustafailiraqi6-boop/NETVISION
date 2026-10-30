package com.netvision.app.settings

enum class AppLanguage(val code: String, val title: String, val isRtl: Boolean) {
    ENGLISH("en", "English", false),
    ARABIC("ar", "العربية", true)
}

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

data class AppSettings(
    val language: AppLanguage = AppLanguage.ARABIC,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val defaultSubnet: String = "192.168.1.0/24",
    val scanTimeoutMs: Int = 1200,
    val pingRetries: Int = 2,
    val enableUbiquitiDiscovery: Boolean = true,
    val enableMikroTikMndp: Boolean = true,
    val enableSnmpPolling: Boolean = true,
    val snmpCommunity: String = "public",
    val monitoringRefreshIntervalSec: Int = 5
)
