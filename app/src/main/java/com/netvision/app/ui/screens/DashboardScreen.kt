package com.netvision.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.netvision.app.models.ActivitySeverity
import com.netvision.app.models.RecentDeviceActivity
import com.netvision.app.ui.theme.*
import com.netvision.app.ui.viewmodels.DashboardViewModel
import com.netvision.app.ui.viewmodels.MonitoringViewModel
import java.util.Locale

@Composable
fun DashboardScreen(
    isArabic: Boolean,
    onNavigateToDevices: (DeviceStatus?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel(),
    monitoringViewModel: MonitoringViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val monitoringState by monitoringViewModel.uiState.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // App Header
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "NETVISION",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = if (isArabic) "اكتشاف ومراقبة شبكات Ubiquiti و MikroTik" else "Ubiquiti & MikroTik Discovery & Monitoring",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isArabic) "نظام حي" else "Live System",
                                fontSize = 11.sp,
                                color = CyanAccent
                            )
                        }
                    }
                }
            }
        }

        // Network Health Overview Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isArabic) "نظرة عامة على صحة الشبكة" else "Network Health Overview",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { monitoringViewModel.refreshNow() },
                                enabled = !monitoringState.isPollingNow,
                                modifier = Modifier.size(34.dp)
                            ) {
                                if (monitoringState.isPollingNow) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(19.dp),
                                        strokeWidth = 2.dp,
                                        color = CyanAccent
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = if (isArabic) "تحديث الأجهزة" else "Refresh devices",
                                        tint = CyanAccent
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (uiState.offlineDevices == 0) StatusGreen else StatusAmber)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.totalDevices > 0) "${uiState.healthPercentage}%" else (if (isArabic) "جاهز" else "Ready"),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.offlineDevices == 0) StatusGreen else StatusAmber
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HealthMetricItem(
                            label = if (isArabic) "متوسط التأخير" else "Avg Latency",
                            value = if (uiState.avgLatencyMs > 0) String.format(Locale.US, "%.1f ms", uiState.avgLatencyMs) else "—",
                            icon = Icons.Default.Speed
                        )
                        HealthMetricItem(
                            label = if (isArabic) "أجهزة متصلة" else "Online Ratio",
                            value = "${uiState.onlineDevices} / ${uiState.totalDevices}",
                            icon = Icons.Default.CheckCircle
                        )
                        HealthMetricItem(
                            label = if (isArabic) "أجهزة مفصولة" else "Offline",
                            value = "${uiState.offlineDevices}",
                            icon = Icons.Default.ErrorOutline
                        )
                    }
                }
            }
        }

        // Stat KPI Grid: Total, Online, Offline
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = if (isArabic) "إجمالي الأجهزة" else "Total Devices",
                    count = uiState.totalDevices.toString(),
                    accentColor = CyanAccent,
                    icon = Icons.Default.Devices,
                    onClick = { onNavigateToDevices(null) }
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = if (isArabic) "أجهزة متصلة" else "Online",
                    count = uiState.onlineDevices.toString(),
                    accentColor = StatusGreen,
                    icon = Icons.Default.CheckCircle,
                    onClick = { onNavigateToDevices(DeviceStatus.ONLINE) }
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = if (isArabic) "أجهزة غير متصلة" else "Offline",
                    count = uiState.offlineDevices.toString(),
                    accentColor = StatusRed,
                    icon = Icons.Default.ErrorOutline,
                    onClick = { onNavigateToDevices(DeviceStatus.OFFLINE) }
                )
            }
        }

        // Manufacturer Specific Count (Ubiquiti & MikroTik)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VendorBadgeCard(
                    modifier = Modifier.weight(1f),
                    vendorName = "Ubiquiti (UBNT)",
                    deviceCount = "${uiState.ubntDevicesCount} Devices",
                    deviceCountAr = "${uiState.ubntDevicesCount} أجهزة",
                    protocol = "UniFi / airMAX",
                    color = ElectricBlue,
                    isArabic = isArabic
                )
                VendorBadgeCard(
                    modifier = Modifier.weight(1f),
                    vendorName = "MikroTik",
                    deviceCount = "${uiState.mikrotikDevicesCount} Devices",
                    deviceCountAr = "${uiState.mikrotikDevicesCount} أجهزة",
                    protocol = "RouterOS / Winbox",
                    color = CyanAccent,
                    isArabic = isArabic
                )
            }
        }

        // Recent Device Activity Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) "النشاط والأحداث الأخيرة" else "Recent Network Activity",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isArabic) "عرض الأجهزة" else "View Devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyanAccent,
                    modifier = Modifier.clickable { onNavigateToDevices(null) }
                )
            }
        }

        // Recent Activity List or Empty state
        if (uiState.recentActivity.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isArabic) "لا توجد أنشطة مسجلة بعد.\nعند اكتشاف وإضافة الأجهزة أو تغيير حالتها ستظهر السجلات هنا تلقائياً."
                            else "No activity logged yet.\nReal-time status changes and discovery events will appear here.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(uiState.recentActivity, key = { it.id }) { act ->
                ActivityRow(activity = act, isArabic = isArabic)
            }
        }
    }
}

@Composable
fun HealthMetricItem(
    label: String,
    value: String,
    icon: ImageVector
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun StatCard(
    title: String,
    count: String,
    accentColor: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = count, fontSize = 24.sp, color = accentColor)
        }
    }
}

@Composable
fun VendorBadgeCard(
    vendorName: String,
    deviceCount: String,
    deviceCountAr: String,
    protocol: String,
    color: Color,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = vendorName, style = MaterialTheme.typography.titleSmall, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isArabic) deviceCountAr else deviceCount,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = protocol, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ActivityRow(
    activity: RecentDeviceActivity,
    isArabic: Boolean
) {
    val indicatorColor = when (activity.severity) {
        ActivitySeverity.SUCCESS -> StatusGreen
        ActivitySeverity.WARNING -> StatusAmber
        ActivitySeverity.ALERT -> StatusRed
        ActivitySeverity.INFO -> ElectricBlue
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = activity.deviceName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isArabic) activity.timeAgoAr else activity.timeAgoEn,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isArabic) activity.messageAr else activity.messageEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
