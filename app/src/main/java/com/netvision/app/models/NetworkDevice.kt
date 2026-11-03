package com.netvision.app.models

enum class DeviceType(val labelEn: String, val labelAr: String) {
    LINK("Link", "لنك"),
    ROUTER("Router", "راوتر"),
    SECTOR_AP("Sector / Access Point", "سكتر / نقطة وصول"),
    SWITCH("Switch", "سويتش"),
    CLIENT_DEVICE("Client Device", "جهاز مشترك"),
    ACCESS_POINT("Access Point", "نقطة وصول"),
    GATEWAY("Security Gateway", "بوابة حماية"),
    CONTROLLER("Controller", "وحدة تحكم"),
    OTHER("Other", "أخرى")
}

enum class Manufacturer(val displayName: String) {
    MIKROTIK("MikroTik"),
    UBIQUITI("Ubiquiti (UBNT)"),
    MIMOSA("Mimosa Networks"),
    CISCO("Cisco"),
    OTHER("Other"),
    GENERIC("Generic / Other")
}

data class DeviceFolder(
    val id: String,
    val name: String,
    val deviceCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

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
    val snmpVersion: String? = null,
    val folderId: String = "uncategorized"
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
