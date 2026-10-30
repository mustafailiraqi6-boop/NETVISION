package com.netvision.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.repository.DeviceRepository
import com.netvision.app.ui.theme.*

@Composable
fun MonitoringScreen(
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val repository = remember { DeviceRepository() }
    val allDevices by repository.getAllDevices().collectAsState(initial = emptyList())
    var selectedDevice by remember { mutableStateOf<NetworkDevice?>(null) }

    LaunchedEffect(allDevices) {
        if (selectedDevice == null && allDevices.isNotEmpty()) {
            selectedDevice = allDevices.first()
        }
    }

    val active = selectedDevice ?: allDevices.firstOrNull()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Screen Header
        item {
            Column {
                Text(
                    text = if (isArabic) "مراقبة الأجهزة الحية" else "Device Telemetry & Monitoring",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isArabic) "مؤشرات SNMP v2c/v3 وواجهات قياس الأداء" else "Real-time metrics, bandwidth utilization, and latency counters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Clearly identified placeholder values notice per prompt instructions
                Surface(
                    color = ElectricBlueContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Text(
                        text = if (isArabic) "ℹ️ قيم قياس أولية: يتم استخدام قيم قياس استرشادية حتى اكتمال الربط المباشر بمكتبة بروتوكول SNMP."
                        else "ℹ️ Prototype SNMP Telemetry: Placeholder metric counters displayed prior to live socket SNMP daemon polling.",
                        fontSize = 11.sp,
                        color = CyanAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Device Selector Row
        item {
            Column {
                Text(
                    text = if (isArabic) "اختر الجهاز للمراقبة:" else "Select Monitored Device:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(allDevices) { dev ->
                        val isSelected = active?.id == dev.id
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDevice = dev },
                            label = { Text(dev.name) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (dev.status == DeviceStatus.ONLINE) StatusGreen else StatusRed)
                                )
                            }
                        )
                    }
                }
            }
        }

        if (active != null) {
            // Selected Device Overview Header Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = active.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${active.manufacturer.displayName} • ${active.model} (${active.ipAddress})",
                                    fontSize = 12.sp,
                                    color = CyanAccent
                                )
                            }
                            Text(
                                text = "SNMP v2c",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Metric Cards Grid
            // 1. Ping Latency & Uptime
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricDashboardCard(
                        modifier = Modifier.weight(1f),
                        title = if (isArabic) "زمن الاستجابة (Ping)" else "Ping Latency",
                        value = "${active.metrics.pingLatencyMs} ms",
                        subtext = if (active.metrics.pingLatencyMs < 10) "Stable ICMP echo" else "High jitter noticed",
                        icon = Icons.Default.Speed,
                        tint = if (active.metrics.pingLatencyMs < 20) StatusGreen else StatusAmber
                    )
                    MetricDashboardCard(
                        modifier = Modifier.weight(1f),
                        title = if (isArabic) "مدة التشغيل (Uptime)" else "Device Uptime",
                        value = active.metrics.uptimeFormatted,
                        subtext = "Continuous operation",
                        icon = Icons.Default.Timer,
                        tint = ElectricBlue
                    )
                }
            }

            // 2. CPU Usage & Memory Usage
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricDashboardCard(
                        modifier = Modifier.weight(1f),
                        title = if (isArabic) "استهلاك المعالج" else "CPU Load",
                        value = "${active.metrics.cpuUsagePercent}%",
                        subtext = "Quad-core ARM Cortex",
                        icon = Icons.Default.Memory,
                        tint = if (active.metrics.cpuUsagePercent > 70) StatusAmber else CyanAccent
                    )
                    MetricDashboardCard(
                        modifier = Modifier.weight(1f),
                        title = if (isArabic) "استهلاك الذاكرة" else "Memory Usage",
                        value = "${active.metrics.memoryUsagePercent}%",
                        subtext = "4GB DDR4 allocated",
                        icon = Icons.Default.Storage,
                        tint = CyanAccent
                    )
                }
            }

            // 3. Traffic TX / RX
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) "حركة البيانات (TX / RX Traffic)" else "Interface Bandwidth (TX/RX)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Icon(Icons.Default.SwapVert, contentDescription = null, tint = CyanAccent)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = if (isArabic) "معدل الإرسال (TX)" else "Transmit (TX)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${active.metrics.txRateMbps} Mbps",
                                    fontSize = 20.sp,
                                    color = ElectricBlue
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = if (isArabic) "معدل الاستقبال (RX)" else "Receive (RX)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${active.metrics.rxRateMbps} Mbps",
                                    fontSize = 20.sp,
                                    color = CyanAccent
                                )
                            }
                        }
                    }
                }
            }

            // 4. Wireless Signal Strength (dBm) for APs / Radios
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (isArabic) "قوة الإشارة اللاسلكية" else "RF Signal Strength",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "RSSI: ${active.metrics.signalDbm} dBm (Good Link Quality)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = StatusGreen, modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MetricDashboardCard(
    title: String,
    value: String,
    subtext: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtext, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
