package com.netvision.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netvision.app.models.NetworkDevice
import com.netvision.app.monitoring.UbiquitiStationInfo
import com.netvision.app.monitoring.MikrotikStationInfo
import com.netvision.app.ui.theme.*
import com.netvision.app.ui.viewmodels.MonitoringViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSnmpDetailsScreen(
    deviceId: String,
    isArabic: Boolean,
    onBack: () -> Unit,
    viewModel: MonitoringViewModel = viewModel()
) {
    val devices by viewModel.savedDevices.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val device = devices.firstOrNull { it.id == deviceId }
    val info = state.snmpStatusByDevice[deviceId]
    val stations = info?.connectedStations.orEmpty()
    val mikrotikStations = info?.connectedMikrotikStations.orEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isArabic) "الأجهزة المتصلة بالسكتر" else "Connected Stations") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = if (isArabic) "رجوع" else "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshNow() }) {
                        Icon(Icons.Default.Refresh, contentDescription = if (isArabic) "تحديث" else "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        if (device == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if (isArabic) "الجهاز غير موجود" else "Device not found")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(device.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("IP: ${device.ipAddress}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isArabic) "المحطات المتصلة" else "Connected stations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(info?.connectedClientsCount?.toString() ?: "—", color = TealAccent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }

                if (mikrotikStations.isNotEmpty()) {
                    items(mikrotikStations, key = { it.macAddress }) { station ->
                        MikrotikStationCard(station, isArabic)
                    }
                } else if (stations.isNotEmpty()) {
                    items(stations, key = { it.macAddress }) { station ->
                        UbiquitiStationCard(station, isArabic)
                    }
                } else if (!info?.connectedStationsMacs.isNullOrEmpty()) {
                    items(info!!.connectedStationsMacs.distinct(), key = { it }) { mac ->
                        StationMacCard(mac, isArabic)
                    }
                } else {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = if (info?.isSuccess == true && info.connectedClientsCount == 0) {
                                    if (isArabic) "لا توجد محطات متصلة حالياً." else "No stations are currently connected."
                                } else {
                                    if (isArabic) "تفاصيل المحطات غير متاحة من SNMP لهذا الجهاز." else "Station details are unavailable through SNMP on this device."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tileBackground = if (isDark) foreground.copy(alpha = 0.16f) else background
    val tileForeground = if (isDark) foreground.copy(alpha = 0.98f) else foreground
    Column(
        modifier = modifier.background(tileBackground, RoundedCornerShape(10.dp)).padding(horizontal = 7.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = tileForeground, fontSize = 10.sp, maxLines = 1)
        Text(value, color = if (isDark) MaterialTheme.colorScheme.onSurface else tileForeground, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun UbiquitiStationCard(station: UbiquitiStationInfo, isArabic: Boolean) {
    val clipboard = LocalClipboardManager.current
    val stationName = station.deviceName?.takeIf { it.isNotBlank() }
        ?: if (isArabic) "اسم المحطة غير متاح" else "Station name unavailable"
    fun rateMbps(value: Int?): String = value?.let {
        val mbps = it.toDouble() / 1_000_000.0
        String.format(Locale.US, if (mbps >= 10) "%.0f Mbps" else "%.1f Mbps", mbps)
    } ?: "—"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(color = StatusGreen.copy(alpha = 0.13f), shape = RoundedCornerShape(8.dp)) {
                    Text(if (isArabic) "متصل" else "Online", color = StatusGreen, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                }
            }
            Text(station.signalDbm?.let { "$it dBm" } ?: "—", color = StatusGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(station.macAddress, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                IconButton(onClick = { clipboard.setText(AnnotatedString(station.macAddress)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = if (isArabic) "نسخ MAC" else "Copy MAC", tint = TealAccent)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MetricTile("TX", rateMbps(station.txRate), Color(0xFFDDF6FA), Color(0xFF0E7490), Modifier.weight(1f))
                MetricTile("RX", rateMbps(station.rxRate), Color(0xFFE5F0FF), Color(0xFF2563EB), Modifier.weight(1f))
                MetricTile("CCQ", station.ccqPercent?.let { "$it%" } ?: "—", Color(0xFFDCFCE7), Color(0xFF15803D), Modifier.weight(1f))
                MetricTile(if (isArabic) "المسافة" else "Distance", station.distance?.let { "$it m" } ?: "—", Color(0xFFFFF2D9), Color(0xFFB7791F), Modifier.weight(1f))
            }
            Row(
                Modifier.fillMaxWidth().background(if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF34264F) else Color(0xFFEEE8FF), RoundedCornerShape(10.dp)).padding(11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isArabic) "مدة الاتصال" else "Connection time", color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFD8C6FF) else Color(0xFF6542A6), fontSize = 12.sp)
                Text(station.connectionTime ?: "—", color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFD8C6FF) else Color(0xFF6542A6), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MikrotikStationCard(station: MikrotikStationInfo, isArabic: Boolean) {
    val clipboard = LocalClipboardManager.current
    val stationName = station.deviceName?.takeIf { it.isNotBlank() }
        ?: if (isArabic) "اسم المحطة غير متاح" else "Station name unavailable"
    fun rate(value: Double?): String = value?.let {
        val formatted = if (it % 1.0 == 0.0) it.toLong().toString() else String.format(Locale.US, "%.1f", it)
        "$formatted Mbps"
    } ?: "—"

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stationName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(color = StatusGreen.copy(alpha = 0.13f), shape = RoundedCornerShape(8.dp)) {
                    Text(if (isArabic) "متصل" else "Online", color = StatusGreen, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                }
            }
            Text(station.signalDbm?.let { "$it dBm" } ?: "—", color = StatusGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(station.macAddress, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                IconButton(onClick = { clipboard.setText(AnnotatedString(station.macAddress)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = if (isArabic) "نسخ MAC" else "Copy MAC", tint = TealAccent)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile("TX", rate(station.txRateMbps), Color(0xFFDDF6FA), Color(0xFF0E7490), Modifier.weight(1f))
                MetricTile("RX", rate(station.rxRateMbps), Color(0xFFE5F0FF), Color(0xFF2563EB), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth().background(if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF34264F) else Color(0xFFEEE8FF), RoundedCornerShape(10.dp)).padding(11.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (isArabic) "مدة الاتصال" else "Connection time", color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFD8C6FF) else Color(0xFF6542A6), fontSize = 12.sp)
                Text(station.connectionTime ?: "—", color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFD8C6FF) else Color(0xFF6542A6), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun StationMacCard(mac: String, isArabic: Boolean) {
    val clipboard = LocalClipboardManager.current
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (isArabic) "محطة غير معروفة" else "Unknown station", fontWeight = FontWeight.Bold)
                Text(mac, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = { clipboard.setText(AnnotatedString(mac)) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = if (isArabic) "نسخ MAC" else "Copy MAC", tint = TealAccent)
            }
        }
    }
}
