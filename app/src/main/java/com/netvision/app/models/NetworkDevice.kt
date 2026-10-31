package com.netvision.app.models

enum class DeviceType(val labelEn: String, val labelAr: String) {
    ROUTER("Router", "راوتر"),
    SWITCH("Switch", "سويتش"),
    ACCESS_POINT("Access Point", "نقطة وصول"),
    GATEWAY("Security Gateway", "بوابة حماية"),
    CONTROLLER("Controller", "وحدة تحكم")
}

enum class Manufacturer(val displayName: String) {
    UBIQUITI("Ubiquiti (UBNT)"),
    MIKROTIK("MikroTik"),
    CISCO("Cisco"),
    GENERIC("Generic / Other")
}

enum class DeviceStatus(val labelEn: String, val labelAr: String) {
    ONLINE("Online", "متصل"),
    OFFLINE("Offline", "غير متصل"),
    WARNING("High Latency", "تأخير مرتفع")
}

data class NetworkMetrics(
    val signalDbm: Int = -62,
    val txRateMbps: Double = 84.5,
    val rxRateMbps: Double = 142.8,
    val uptimeFormatted: String = "42d 18h 32m",
    val cpuUsagePercent: Int = 18,
    val memoryUsagePercent: Int = 34,
    val pingLatencyMs: Int = 4
)

data class NetworkDevice(
    val id: String,
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val manufacturer: Manufacturer,
    val model: String,
    val deviceType: DeviceType,
    val status: DeviceStatus,
    val firmwareVersion: String = "v3.2.12",
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val metrics: NetworkMetrics = NetworkMetrics(),
    val snmpVersion: String? = null
)

data class NetworkRange(
    val id: String,
    val cidr: String,
    val label: String,
    val isEnabled: Boolean = true
)

data class RecentDeviceActivity(
    val id: String,
    val deviceName: String,
    val ipAddress: String,
    val messageEn: String,
    val messageAr: String,
    val timeAgoEn: String,
    val timeAgoAr: String,
    val severity: ActivitySeverity
)

enum class ActivitySeverity {
    INFO,
    SUCCESS,
    WARNING,
    ALERT
}
