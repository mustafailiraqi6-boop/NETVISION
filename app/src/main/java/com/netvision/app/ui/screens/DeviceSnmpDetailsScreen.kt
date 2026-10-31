package com.netvision.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netvision.app.models.NetworkDevice
import com.netvision.app.monitoring.UbiquitiStationInfo
import com.netvision.app.ui.theme.CyanAccent
import com.netvision.app.ui.viewmodels.MonitoringViewModel

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isArabic) "تفاصيل الجهاز عبر SNMP" else "SNMP Device Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = if (isArabic) "رجوع" else "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshNow() }) {
                        Icon(Icons.Default.Refresh, contentDescription = if (isArabic) "تحديث" else "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        if (device == null) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (isArabic) "الجهاز غير موجود" else "Device not found")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(device.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("IP: ${device.ipAddress}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (info?.isSuccess == true) "SNMP Active" else "SNMP ${if (info == null) "Pending" else "Failed"}",
                                color = if (info?.isSuccess == true) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (info == null) {
                                Spacer(Modifier.height(4.dp))
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(if (isArabic) "معلومات الجهاز عبر SNMP" else "SNMP Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            SnmpDetailRow(if (isArabic) "اسم الجهاز" else "Device name", info?.sysName?.takeIf { it.isNotBlank() } ?: device.name)
                            Divider()
                            SnmpDetailRow(if (isArabic) "مدة التشغيل" else "Uptime", info?.uptimeFormatted ?: (if (isArabic) "غير متاح" else "Unavailable"))
                            Divider()
                            SnmpDetailRow(if (isArabic) "الموديل" else "Model", info?.model ?: device.model.ifBlank { if (isArabic) "غير متاح" else "Unavailable" })
                            Divider()
                            SnmpDetailRow(if (isArabic) "زمن استجابة SNMP" else "SNMP response time", info?.takeIf { it.isSuccess && it.latencyMs > 0 }?.let { "${it.latencyMs} ms" } ?: (if (isArabic) "غير متاح" else "Unavailable"))
                            Divider()
                            SnmpDetailRow(if (isArabic) "عدد الأجهزة المتصلة" else "Connected stations", info?.connectedClientsCount?.toString() ?: (if (isArabic) "غير متاح" else "Unavailable"))
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isArabic) "الأجهزة المتصلة بالسكتر" else "Connected Stations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(info?.connectedClientsCount?.let { "$it" } ?: "—", color = CyanAccent, fontWeight = FontWeight.Bold)
                    }
                }
                if (!info?.connectedStations.isNullOrEmpty()) {
                    items(info!!.connectedStations, key = { it.macAddress }) { station ->
                        UbiquitiStationCard(station = station, isArabic = isArabic)
                    }
                } else if (!info?.connectedStationsMacs.isNullOrEmpty()) {
                    items(info!!.connectedStationsMacs.distinct(), key = { it }) { mac ->
                        StationMacCard(mac = mac, isArabic = isArabic)
                    }
                } else {
                    item {
                        Text(
                            if (info?.isSuccess == true && info.connectedClientsCount == 0) {
                                if (isArabic) "لا توجد محطات متصلة حالياً." else "No stations are currently connected."
                            } else {
                                if (isArabic) "تفاصيل المحطات غير متاحة من SNMP لهذا الجهاز." else "Station details are unavailable through SNMP on this device."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SnmpDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun UbiquitiStationCard(station: UbiquitiStationInfo, isArabic: Boolean) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember(station.macAddress) { mutableStateOf(false) }
    val stationName = station.deviceName?.takeIf { it.isNotBlank() }
        ?: (if (isArabic) "اسم المحطة غير متاح" else "Station name unavailable")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stationName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) {
                            if (isArabic) "إخفاء التفاصيل" else "Hide details"
                        } else {
                            if (isArabic) "عرض التفاصيل" else "View details"
                        }
                    )
                }
            }

            if (expanded) {
                Divider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("MAC Address", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(station.macAddress, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(station.macAddress)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Text(if (isArabic) "نسخ" else "Copy")
                    }
                }
                Divider()
                val metrics = listOf(
                    (if (isArabic) "إشارة الجهاز" else "Signal") to station.signalDbm?.let { "$it dBm" },
                    "CCQ" to station.ccqPercent?.let { "$it%" },
                    (if (isArabic) "Data Rate (TX)" else "Data Rate (TX)") to station.txRate?.let { "$it Mbps" },
                    (if (isArabic) "Data Rate (RX)" else "Data Rate (RX)") to station.rxRate?.let { "$it Mbps" }
                )
                metrics.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { (label, value) ->
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun StationMacCard(mac: String, isArabic: Boolean) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember(mac) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isArabic) "محطة غير معروفة" else "Unknown station", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) { if (isArabic) "إخفاء التفاصيل" else "Hide details" } else { if (isArabic) "عرض التفاصيل" else "View details" })
                }
            }
            if (expanded) {
                Text(mac, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { clipboard.setText(AnnotatedString(mac)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Text(if (isArabic) "نسخ MAC" else "Copy MAC")
                }
                Text(if (isArabic) "بقية التفاصيل غير متاحة لهذه المحطة." else "Other station details are unavailable.")
            }
        }
    }
}
