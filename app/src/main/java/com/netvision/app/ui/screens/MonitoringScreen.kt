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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.NetworkDevice
import com.netvision.app.ui.theme.*
import com.netvision.app.ui.viewmodels.MonitoringViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitoringScreen(
    isArabic: Boolean,
    modifier: Modifier = Modifier,
    viewModel: MonitoringViewModel = viewModel()
) {
    val savedDevices by viewModel.savedDevices.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val activeDevice = savedDevices.find { it.id == uiState.selectedDeviceId } ?: savedDevices.firstOrNull()
    val snmpInfo = activeDevice?.let { uiState.snmpStatusByDevice[it.id] }

    val dateFormat = remember { SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()) }

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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isArabic) "مراقبة الأجهزة الحية" else "Live Device Monitoring",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isArabic) "مراقبة دورية فورية عبر ICMP / TCP Connect و SNMP v2c/v3"
                            else "Periodic live polling via ICMP/TCP Connect & SNMP v2c/v3",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalButton(
                        onClick = { viewModel.refreshNow() },
                        enabled = !uiState.isPollingNow && savedDevices.isNotEmpty(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (uiState.isPollingNow) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyanAccent)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isArabic) "تحديث الآن" else "Refresh")
                    }
                }
            }
        }

        // Empty state when no devices are saved
        if (savedDevices.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonitorHeart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isArabic) "لا توجد أجهزة مضافة للمراقبة حالياً."
                            else "No devices saved for monitoring.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isArabic) "انتقل إلى قسم 'فاحص الشبكة' لتشغيل الفحص وإضافة أجهزة Ubiquiti أو MikroTik لقائمة المراقبة."
                            else "Navigate to the Network Scanner tab to discover and add real Ubiquiti or MikroTik hardware.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Device Selector Row
            item {
                Column {
                    Text(
                        text = if (isArabic) "اختر الجهاز لعرض تفاصيل المراقبة:" else "Select Monitored Device:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(savedDevices) { dev ->
                            val isSelected = activeDevice?.id == dev.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectDevice(dev.id) },
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

            if (activeDevice != null) {
                val dev = activeDevice
                val isOnline = dev.status == DeviceStatus.ONLINE

                // Active Device Card
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(if (isOnline) StatusGreen else StatusRed)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = dev.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${dev.manufacturer.displayName} • ${dev.model} (${dev.ipAddress})",
                                            fontSize = 12.sp,
                                            color = CyanAccent
                                        )
                                    }
                                }

                                Surface(
                                    color = if (isOnline) StatusGreen.copy(alpha = 0.15f) else StatusRed.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isArabic) dev.status.labelAr else dev.status.labelEn,
                                        fontSize = 11.sp,
                                        color = if (isOnline) StatusGreen else StatusRed,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Metric Cards: Ping Latency & Last Seen
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RealMetricCard(
                            modifier = Modifier.weight(1f),
                            title = if (isArabic) "زمن الاستجابة (Latency)" else "Response Time",
                            value = if (isOnline) "${dev.metrics.pingLatencyMs} ms" else "N/A",
                            subtext = if (isOnline) "ICMP / TCP Connect" else (if (isArabic) "انقطع الاتصال" else "Host unreachable"),
                            icon = Icons.Default.Speed,
                            tint = if (isOnline) CyanAccent else StatusRed
                        )

                        RealMetricCard(
                            modifier = Modifier.weight(1f),
                            title = if (isArabic) "آخر مشاهدة" else "Last Seen",
                            value = dateFormat.format(Date(dev.lastSeenTimestamp)),
                            subtext = if (isArabic) "تاريخ آخر استجابة" else "Timestamp",
                            icon = Icons.Default.AccessTime,
                            tint = ElectricBlue
                        )
                    }
                }

                // SNMP Card (Real values only with clear error reason when failed)
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.SettingsEthernet, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isArabic) "معلومات بروتوكول SNMP" else "SNMP Telemetry Details",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                val isSnmpOk = snmpInfo != null && snmpInfo.isSuccess
                                val versionLabel = snmpInfo?.snmpVersion?.uppercase() ?: (activeDevice.snmpVersion?.uppercase() ?: "V2C")
                                Surface(
                                    color = if (isSnmpOk) StatusGreen.copy(alpha = 0.15f) else StatusAmber.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (isSnmpOk) "SNMP $versionLabel Active"
                                        else (if (isArabic) "تنبيه SNMP" else "SNMP Notice"),
                                        fontSize = 11.sp,
                                        color = if (isSnmpOk) StatusGreen else StatusAmber,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (snmpInfo != null && snmpInfo.isSuccess) {
                                snmpInfo.negotiationSummary?.let { summary ->
                                    Text(
                                        text = "${if (isArabic) "حالة التفاوض والإصدار:" else "Protocol Negotiation:"} $summary",
                                        fontSize = 12.sp,
                                        color = CyanAccent
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                snmpInfo.sysName?.let { name ->
                                    Text(
                                        text = "System Name (sysName): $name",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                snmpInfo.uptimeFormatted?.let { uptime ->
                                    Text(
                                        text = "${if (isArabic) "مدة تشغيل الجهاز (Uptime):" else "Device Uptime:"} $uptime",
                                        fontSize = 12.sp,
                                        color = CyanAccent
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                snmpInfo.ifCount?.let { count ->
                                    Text(
                                        text = "${if (isArabic) "عدد واجهات الشبكة (Interfaces):" else "Network Interfaces:"} $count",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                if (snmpInfo.latencyMs > 0) {
                                    Text(
                                        text = "${if (isArabic) "زمن استجابة SNMP:" else "SNMP Response Time:"} ${snmpInfo.latencyMs} ms",
                                        fontSize = 11.sp,
                                        color = CyanAccent
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Text(
                                    text = "${if (isArabic) "واجهة الإرسال:" else "Outbound Interface:"} ${snmpInfo.networkInterface}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                snmpInfo.sysDescr?.let { descr ->
                                    Text(
                                        text = "System Description (sysDescr): $descr",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (snmpInfo != null && !snmpInfo.isSuccess) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = StatusAmber, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = snmpInfo.errorMessage ?: "SNMP Query Failed",
                                        fontSize = 12.sp,
                                        color = StatusAmber
                                    )
                                }
                            } else {
                                Text(
                                    text = if (isArabic) "جاري إرسال استعلام SNMP باكتشاف تلقائي على المنفذ 161..."
                                    else "Initiating SNMP query on UDP port 161...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // SNMP Diagnostic Logs Card
                item {
                    var isExpanded by remember { mutableStateOf(true) }
                    val logs = uiState.diagnosticLogs

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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Analytics,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isArabic) "سجل تشخيص SNMP التشغيلي" else "SNMP Diagnostic Logs",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "${logs.size}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                TextButton(
                                    onClick = { isExpanded = !isExpanded },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isExpanded) {
                                            if (isArabic) "طي" else "Collapse"
                                        } else {
                                            if (isArabic) "عرض" else "Expand"
                                        },
                                        fontSize = 12.sp,
                                        color = CyanAccent
                                    )
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(10.dp))
                                if (logs.isEmpty()) {
                                    Text(
                                        text = if (isArabic) "لا توجد عمليات SNMP مسجلة بعد. اضغط 'تحديث الآن' لبدء الفحص."
                                        else "No SNMP transactions recorded yet. Tap 'Refresh' to poll.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        logs.take(10).forEach { log ->
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = CardDefaults.outlinedCardBorder()
                                            ) {
                                                Column(modifier = Modifier.padding(10.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Surface(
                                                                color = if (log.attemptNumber == 1) CyanAccent.copy(alpha = 0.15f) else StatusAmber.copy(alpha = 0.15f),
                                                                shape = RoundedCornerShape(4.dp)
                                                            ) {
                                                                Text(
                                                                    text = if (log.attemptNumber == 1) "Att 1: ${log.version.uppercase()}" else "Att 2 (Fallback): ${log.version.uppercase()}",
                                                                    fontSize = 10.sp,
                                                                    color = if (log.attemptNumber == 1) CyanAccent else StatusAmber,
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = "${log.targetIp}:${log.port}",
                                                                fontSize = 12.sp,
                                                                color = MaterialTheme.colorScheme.onSurface
                                                            )
                                                        }
                                                        Surface(
                                                            color = if (log.isSuccess) StatusGreen.copy(alpha = 0.15f) else StatusRed.copy(alpha = 0.15f),
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = if (log.isSuccess) "Success (${log.latencyMs}ms)" else "Failed",
                                                                fontSize = 10.sp,
                                                                color = if (log.isSuccess) StatusGreen else StatusRed,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = "Community: ${log.communityMasked} • Interface: ${log.networkInterface} • OID: ${log.oidName}",
                                                        fontSize = 11.sp,
                                                        color = CyanAccent
                                                    )
                                                    Text(
                                                        text = "Tx: ${log.bytesSent} B • Rx: ${log.bytesReceived} B • Summary: ${log.resultSummary}",
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    if (!log.exceptionDetail.isNullOrBlank()) {
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "Detail: ${log.exceptionDetail}",
                                                            fontSize = 10.sp,
                                                            color = StatusAmber
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RealMetricCard(
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
            Text(text = value, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtext, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
