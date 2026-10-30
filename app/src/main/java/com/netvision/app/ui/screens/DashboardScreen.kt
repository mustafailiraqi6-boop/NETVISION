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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netvision.app.models.ActivitySeverity
import com.netvision.app.models.RecentDeviceActivity
import com.netvision.app.repository.DeviceRepository
import com.netvision.app.ui.theme.*

@Composable
fun DashboardScreen(
    isArabic: Boolean,
    onNavigateToDevices: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleActivity = DeviceRepository().getSampleActivity()

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
                            text = if (isArabic) "اكتشاف ومراقبة الشبكات الذكية" else "Smart Network Discovery & Monitoring",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isArabic) "نموذج تجريبي أولي" else "UI Prototype",
                            fontSize = 11.sp,
                            color = CyanAccent
                        )
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
                        Text(
                            text = if (isArabic) "نظرة عامة على صحة الشبكة" else "Network Health Overview",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(StatusGreen)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isArabic) "مستقر (94.2%)" else "Optimal (94.2%)",
                                style = MaterialTheme.typography.bodySmall,
                                color = StatusGreen
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
                            value = "4.2 ms",
                            icon = Icons.Default.Speed
                        )
                        HealthMetricItem(
                            label = if (isArabic) "معدل فقد الحزم" else "Packet Loss",
                            value = "0.02 %",
                            icon = Icons.Default.CompareArrows
                        )
                        HealthMetricItem(
                            label = if (isArabic) "إجمالي البيانات" else "Total Flow",
                            value = "1.8 Gbps",
                            icon = Icons.Default.TrendingUp
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
                    count = "8",
                    accentColor = CyanAccent,
                    icon = Icons.Default.Devices,
                    onClick = onNavigateToDevices
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = if (isArabic) "أجهزة متصلة" else "Online",
                    count = "6",
                    accentColor = StatusGreen,
                    icon = Icons.Default.CheckCircle,
                    onClick = onNavigateToDevices
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = if (isArabic) "أجهزة غير متصلة" else "Offline",
                    count = "2",
                    accentColor = StatusRed,
                    icon = Icons.Default.ErrorOutline,
                    onClick = onNavigateToDevices
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
                    deviceCount = "4 Devices",
                    deviceCountAr = "4 أجهزة",
                    protocol = "UBNT UDP 10001",
                    color = ElectricBlue,
                    isArabic = isArabic
                )
                VendorBadgeCard(
                    modifier = Modifier.weight(1f),
                    vendorName = "MikroTik",
                    deviceCount = "3 Devices",
                    deviceCountAr = "3 أجهزة",
                    protocol = "MNDP UDP 5678",
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
                    text = if (isArabic) "النشاط الأخير للأجهزة" else "Recent Device Activity",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isArabic) "عرض الكل" else "View All",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyanAccent,
                    modifier = Modifier.clickable { onNavigateToDevices() }
                )
            }
        }

        // Recent Activity List
        items(sampleActivity) { act ->
            ActivityRow(activity = act, isArabic = isArabic)
        }
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
