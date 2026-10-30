package com.netvision.app.repository

import com.netvision.app.database.DeviceDao
import com.netvision.app.database.DeviceEntity
import com.netvision.app.models.ActivitySeverity
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.DeviceType
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.NetworkMetrics
import com.netvision.app.models.RecentDeviceActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class DeviceRepository(private val deviceDao: DeviceDao? = null) {

    // Initial prototype sample data clearly labeled as sample data per development rules
    private val sampleDevices = listOf(
        NetworkDevice(
            id = "ubnt-udm-01",
            name = "UniFi Dream Machine Pro",
            ipAddress = "192.168.1.1",
            macAddress = "74:83:C2:11:44:88",
            manufacturer = Manufacturer.UBIQUITI,
            model = "UDM-Pro",
            deviceType = DeviceType.GATEWAY,
            status = DeviceStatus.ONLINE,
            firmwareVersion = "UniFi OS 3.2.12",
            metrics = NetworkMetrics(
                signalDbm = -54,
                txRateMbps = 240.5,
                rxRateMbps = 680.2,
                uptimeFormatted = "84d 14h 22m",
                cpuUsagePercent = 24,
                memoryUsagePercent = 48,
                pingLatencyMs = 2
            )
        ),
        NetworkDevice(
            id = "mtk-ccr-01",
            name = "MikroTik CCR2004 Core",
            ipAddress = "192.168.1.2",
            macAddress = "48:8F:5A:23:BC:10",
            manufacturer = Manufacturer.MIKROTIK,
            model = "CCR2004-16G-2S+",
            deviceType = DeviceType.ROUTER,
            status = DeviceStatus.ONLINE,
            firmwareVersion = "RouterOS v7.14.3",
            metrics = NetworkMetrics(
                signalDbm = -58,
                txRateMbps = 310.2,
                rxRateMbps = 520.6,
                uptimeFormatted = "112d 06h 40m",
                cpuUsagePercent = 12,
                memoryUsagePercent = 29,
                pingLatencyMs = 1
            )
        ),
        NetworkDevice(
            id = "ubnt-ap-01",
            name = "UniFi U6 Enterprise AP",
            ipAddress = "192.168.1.20",
            macAddress = "AC:8B:A9:72:3F:4A",
            manufacturer = Manufacturer.UBIQUITI,
            model = "U6-Enterprise",
            deviceType = DeviceType.ACCESS_POINT,
            status = DeviceStatus.ONLINE,
            firmwareVersion = "6.6.65",
            metrics = NetworkMetrics(
                signalDbm = -65,
                txRateMbps = 115.4,
                rxRateMbps = 298.1,
                uptimeFormatted = "32d 19h 10m",
                cpuUsagePercent = 38,
                memoryUsagePercent = 52,
                pingLatencyMs = 6
            )
        ),
        NetworkDevice(
            id = "mtk-hex-01",
            name = "MikroTik hEX S Branch",
            ipAddress = "192.168.1.5",
            macAddress = "08:55:31:8B:FE:09",
            manufacturer = Manufacturer.MIKROTIK,
            model = "RB760iGS",
            deviceType = DeviceType.ROUTER,
            status = DeviceStatus.WARNING,
            firmwareVersion = "RouterOS v7.13.1",
            metrics = NetworkMetrics(
                signalDbm = -72,
                txRateMbps = 42.1,
                rxRateMbps = 89.3,
                uptimeFormatted = "14d 02h 15m",
                cpuUsagePercent = 78,
                memoryUsagePercent = 82,
                pingLatencyMs = 48
            )
        ),
        NetworkDevice(
            id = "ubnt-sw-01",
            name = "UniFi Switch Pro 24 PoE",
            ipAddress = "192.168.1.10",
            macAddress = "68:D7:9A:3C:99:E1",
            manufacturer = Manufacturer.UBIQUITI,
            model = "USW-Pro-24-PoE",
            deviceType = DeviceType.SWITCH,
            status = DeviceStatus.ONLINE,
            firmwareVersion = "6.6.61",
            metrics = NetworkMetrics(
                signalDbm = -50,
                txRateMbps = 195.0,
                rxRateMbps = 340.5,
                uptimeFormatted = "95d 11h 04m",
                cpuUsagePercent = 19,
                memoryUsagePercent = 35,
                pingLatencyMs = 3
            )
        ),
        NetworkDevice(
            id = "ubnt-airmax-01",
            name = "Ubiquiti airMAX LiteBeam 5AC",
            ipAddress = "192.168.1.50",
            macAddress = "24:A4:3C:D0:62:1B",
            manufacturer = Manufacturer.UBIQUITI,
            model = "LBE-5AC-Gen2",
            deviceType = DeviceType.ACCESS_POINT,
            status = DeviceStatus.ONLINE,
            firmwareVersion = "airOS v8.7.12",
            metrics = NetworkMetrics(
                signalDbm = -61,
                txRateMbps = 65.0,
                rxRateMbps = 120.4,
                uptimeFormatted = "67d 08h 19m",
                cpuUsagePercent = 41,
                memoryUsagePercent = 44,
                pingLatencyMs = 9
            )
        ),
        NetworkDevice(
            id = "mtk-crs-01",
            name = "MikroTik CRS328 Switch",
            ipAddress = "192.168.1.15",
            macAddress = "CC:2D:E0:45:98:AA",
            manufacturer = Manufacturer.MIKROTIK,
            model = "CRS328-24P-4S+RM",
            deviceType = DeviceType.SWITCH,
            status = DeviceStatus.ONLINE,
            firmwareVersion = "SwOS 2.16",
            metrics = NetworkMetrics(
                signalDbm = -55,
                txRateMbps = 280.0,
                rxRateMbps = 410.0,
                uptimeFormatted = "51d 16h 02m",
                cpuUsagePercent = 15,
                memoryUsagePercent = 22,
                pingLatencyMs = 2
            )
        ),
        NetworkDevice(
            id = "cisco-core-01",
            name = "Cisco Catalyst 3850",
            ipAddress = "192.168.1.254",
            macAddress = "00:1E:13:C2:55:60",
            manufacturer = Manufacturer.CISCO,
            model = "WS-C3850-48P",
            deviceType = DeviceType.SWITCH,
            status = DeviceStatus.OFFLINE,
            firmwareVersion = "IOS-XE 16.12.5b",
            metrics = NetworkMetrics(
                signalDbm = 0,
                txRateMbps = 0.0,
                rxRateMbps = 0.0,
                uptimeFormatted = "Offline",
                cpuUsagePercent = 0,
                memoryUsagePercent = 0,
                pingLatencyMs = 0
            )
        )
    )

    fun getAllDevices(): Flow<List<NetworkDevice>> {
        return deviceDao?.getAllDevices()?.map { list ->
            list.map { it.toDomainModel() }
        } ?: flowOf(sampleDevices)
    }

    fun getSampleActivity(): List<RecentDeviceActivity> {
        return listOf(
            RecentDeviceActivity(
                id = "act-1",
                deviceName = "UniFi Dream Machine Pro",
                ipAddress = "192.168.1.1",
                messageEn = "Firmware update check completed. WAN latency steady at 2ms.",
                messageAr = "تم فحص تحديث النظام بنجاح. زمن استجابة منفذ WAN ثابت عند 2ms.",
                timeAgoEn = "4 mins ago",
                timeAgoAr = "منذ 4 دقائق",
                severity = ActivitySeverity.SUCCESS
            ),
            RecentDeviceActivity(
                id = "act-2",
                deviceName = "MikroTik hEX S Branch",
                ipAddress = "192.168.1.5",
                messageEn = "High CPU load detected (78%) - BGP routing table re-syncing.",
                messageAr = "ارتفاع استهلاك المعالج (78%) - إعادة مزامنة جدول توجيه BGP.",
                timeAgoEn = "18 mins ago",
                timeAgoAr = "منذ 18 دقيقة",
                severity = ActivitySeverity.WARNING
            ),
            RecentDeviceActivity(
                id = "act-3",
                deviceName = "Cisco Catalyst 3850",
                ipAddress = "192.168.1.254",
                messageEn = "ICMP Echo timeout - device unreachable across VLAN 10.",
                messageAr = "انتهاء مهلة فحص ICMP - الجهاز لا يستجيب عبر شبكة VLAN 10.",
                timeAgoEn = "1 hour ago",
                timeAgoAr = "منذ ساعة واحدة",
                severity = ActivitySeverity.ALERT
            ),
            RecentDeviceActivity(
                id = "act-4",
                deviceName = "UniFi U6 Enterprise AP",
                ipAddress = "192.168.1.20",
                messageEn = "68 active wireless clients connected across 5GHz/6GHz bands.",
                messageAr = "68 عميل لاسلكي متصل بنطاقي 5GHz و 6GHz.",
                timeAgoEn = "2 hours ago",
                timeAgoAr = "منذ ساعتين",
                severity = ActivitySeverity.INFO
            )
        )
    }
}
