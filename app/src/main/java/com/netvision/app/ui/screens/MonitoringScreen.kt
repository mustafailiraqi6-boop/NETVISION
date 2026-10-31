package com.netvision.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.netvision.app.database.DeviceFolderEntity
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.ui.theme.*
import com.netvision.app.ui.viewmodels.MonitoringViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(
    isArabic: Boolean,
    onViewDeviceDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MonitoringViewModel = viewModel()
) {
    val savedDevices by viewModel.savedDevices.collectAsState()
    val savedFolders by viewModel.savedFolders.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // Filtering devices based on selected tower and search query
    val filteredDevices = remember(savedDevices, uiState.selectedFolderId, uiState.searchQuery) {
        savedDevices.filter { dev ->
            val matchesFolder = when (uiState.selectedFolderId) {
                null -> true
                DeviceFolderEntity.DEFAULT_FOLDER_ID -> dev.folderId.isBlank() || dev.folderId == DeviceFolderEntity.DEFAULT_FOLDER_ID
                else -> dev.folderId == uiState.selectedFolderId
            }
            val matchesQuery = uiState.searchQuery.isBlank() ||
                    dev.name.contains(uiState.searchQuery, ignoreCase = true) ||
                    dev.ipAddress.contains(uiState.searchQuery)
            matchesFolder && matchesQuery
        }
    }

    // Currently selected active device for SNMP details card
    val activeDevice = savedDevices.find { it.id == uiState.selectedDeviceId }
        ?: filteredDevices.firstOrNull()
        ?: savedDevices.firstOrNull()


    // Dynamic stats based on saved devices
    val totalDevicesCount = savedDevices.size
    val connectedCount = savedDevices.count { it.status == DeviceStatus.ONLINE }
    val disconnectedCount = savedDevices.count { it.status == DeviceStatus.OFFLINE }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // -------------------------------------------------------------
        // Header (عنوان رئيسي + وصف + زر تحديث)
        // -------------------------------------------------------------
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
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isArabic) "مراقبة حالة الأجهزة وبيانات SNMP عبر شبكة VPN"
                            else "Live status polling & SNMP telemetry over OpenVPN network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledTonalButton(
                        onClick = { viewModel.refreshNow() },
                        enabled = !uiState.isPollingNow && savedDevices.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CyanAccent.copy(alpha = 0.15f),
                            contentColor = CyanAccent
                        )
                    ) {
                        if (uiState.isPollingNow) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyanAccent)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isArabic) "تحديث الآن" else "Refresh",
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Statistics Cards (إجمالي الأجهزة، الأجهزة المتصلة، الأجهزة المنفصلة)
        // -------------------------------------------------------------
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MonitoringStatCard(
                    modifier = Modifier.weight(1f),
                    title = if (isArabic) "إجمالي الأجهزة" else "Total Devices",
                    count = totalDevicesCount.toString(),
                    accentColor = CyanAccent,
                    icon = Icons.Default.Devices
                )
                MonitoringStatCard(
                    modifier = Modifier.weight(1f),
                    title = if (isArabic) "الأجهزة المتصلة" else "Connected",
                    count = connectedCount.toString(),
                    accentColor = StatusGreen,
                    icon = Icons.Default.CheckCircle
                )
                MonitoringStatCard(
                    modifier = Modifier.weight(1f),
                    title = if (isArabic) "الأجهزة المنفصلة" else "Disconnected",
                    count = disconnectedCount.toString(),
                    accentColor = StatusRed,
                    icon = Icons.Default.ErrorOutline
                )
            }
        }

        // -------------------------------------------------------------
        // Tower Selector Card (اختيار البرج)
        // -------------------------------------------------------------
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CellTower,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isArabic) "اختيار البرج:" else "Select Tower:",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val selectedTowerName = when (uiState.selectedFolderId) {
                            null -> if (isArabic) "جميع الأبراج" else "All Towers"
                            DeviceFolderEntity.DEFAULT_FOLDER_ID -> if (isArabic) "أجهزة غير تابعة لبرج" else "Unassigned Devices"
                            else -> savedFolders.find { it.id == uiState.selectedFolderId }?.name ?: ""
                        }

                        Surface(
                            color = CyanAccent.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = selectedTowerName,
                                fontSize = 11.sp,
                                color = CyanAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // " جميع الأبراج"
                        item {
                            FilterChip(
                                selected = uiState.selectedFolderId == null,
                                onClick = { viewModel.selectFolder(null) },
                                label = { Text(if (isArabic) "جميع الأبراج ($totalDevicesCount)" else "All Towers ($totalDevicesCount)") },
                                leadingIcon = {
                                    Icon(Icons.Default.CellTower, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }

                        // Towers list
                        items(savedFolders) { folder ->
                            val isSelected = uiState.selectedFolderId == folder.id
                            val isUnassigned = folder.id == DeviceFolderEntity.DEFAULT_FOLDER_ID
                            val labelText = if (isUnassigned) {
                                if (isArabic) "أجهزة غير تابعة لبرج (${folder.deviceCount})" else "Unassigned (${folder.deviceCount})"
                            } else {
                                "${folder.name} (${folder.deviceCount})"
                            }

                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectFolder(folder.id) },
                                label = { Text(labelText) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isUnassigned) Icons.Default.FolderOpen else Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // Search Bar (البحث باسم الجهاز أو IP)
        // -------------------------------------------------------------
        item {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = if (isArabic) "بحث باسم الجهاز أو عنوان IP..." else "Search by device name or IP...",
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CyanAccent)
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        // -------------------------------------------------------------
        // Devices Card List Header
        // -------------------------------------------------------------
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) "قائمة الأجهزة (${filteredDevices.size})" else "Monitored Devices (${filteredDevices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (filteredDevices.isNotEmpty()) {
                    Text(
                        text = if (isArabic) "اضغط على الجهاز لعرض تفاصيل SNMP" else "Tap device to view SNMP details",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Empty state when no devices match
        if (filteredDevices.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.DevicesOther,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (savedDevices.isEmpty()) {
                                if (isArabic) "لا توجد أجهزة مضافة للمراقبة حالياً."
                                else "No devices added for monitoring."
                            } else {
                                if (isArabic) "لا توجد أجهزة مطابقة للبحث أو للبرج المSelected."
                                else "No devices match search query or selected tower."
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
            // Scrollable vertical list of device cards
            items(filteredDevices, key = { it.id }) { device ->
                val isSelected = activeDevice?.id == device.id
                val devSnmp = uiState.snmpStatusByDevice[device.id]
                val isSnmpActive = devSnmp?.isSuccess == true

                DeviceMonitoringCard(
                    device = device,
                    isSelected = isSelected,
                    isSnmpActive = isSnmpActive,
                    isArabic = isArabic,
                    onClick = { viewModel.selectDevice(device.id) },
                    onViewDetails = { onViewDeviceDetails(device.id) }
                )
            }
        }

    }
}

@Composable
fun DeviceMonitoringCard(
    device: NetworkDevice,
    isSelected: Boolean,
    isSnmpActive: Boolean,
    isArabic: Boolean,
    onClick: () -> Unit,
    onViewDetails: () -> Unit
) {
    val isOnline = device.status == DeviceStatus.ONLINE
    val statusColor = if (isOnline) StatusGreen else StatusRed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) CyanAccent else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                }
                Surface(color = statusColor.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = if (isOnline) (if (isArabic) "متصل" else "Online") else (if (isArabic) "غير متصل" else "Offline"),
                        color = statusColor,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("IP: ${device.ipAddress}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Surface(
                    color = (if (isSnmpActive) StatusGreen else StatusAmber).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        if (isSnmpActive) "SNMP Active" else "SNMP Failed",
                        color = if (isSnmpActive) StatusGreen else StatusAmber,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = onViewDetails,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Text(if (isArabic) "عرض التفاصيل" else "View details", fontSize = 12.sp)
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun MonitoringStatCard(
    title: String,
    count: String,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
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
            Text(text = count, fontSize = 22.sp, color = accentColor, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}

@Composable
fun SnmpFieldRow(
    label: String,
    value: String,
    isArabic: Boolean,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = valueColor,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            textAlign = if (isArabic) androidx.compose.ui.text.style.TextAlign.Start else androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(0.58f),
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
