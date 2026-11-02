package com.netvision.app.ui.screens

import android.content.Intent
import android.net.Uri

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
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netvision.app.database.DeviceFolderEntity
import com.netvision.app.models.DeviceFolder
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.DeviceType
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.monitoring.EthernetMonitoringSnapshot
import com.netvision.app.monitoring.EthernetPortSnapshot
import com.netvision.app.ui.theme.*
import com.netvision.app.ui.viewmodels.MonitoringViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun compareIpv4Addresses(first: String, second: String): Int {
    fun toNumericAddress(address: String): Long? {
        val parts = address.substringBefore('/').split('.')
        if (parts.size != 4) return null
        var value = 0L
        for (part in parts) {
            val octet = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            value = (value shl 8) or octet.toLong()
        }
        return value
    }
    val a = toNumericAddress(first)
    val b = toNumericAddress(second)
    return when {
        a != null && b != null -> a.compareTo(b)
        a != null -> -1
        b != null -> 1
        else -> first.compareTo(second, ignoreCase = true)
    }
}

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
    var selectedCategory by remember { mutableStateOf(false) } // false: sectors/devices, true: switches
    var selectedSwitchId by remember { mutableStateOf<String?>(null) }

    val selectedSwitch = savedDevices.firstOrNull { it.id == selectedSwitchId }

    if (selectedSwitch != null) {
        Dialog(onDismissRequest = { selectedSwitchId = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
            SwitchPortsDetailPage(
                device = selectedSwitch,
                snapshot = uiState.ethernetPortsByDevice[selectedSwitch.id],
                isArabic = isArabic,
                onBack = { selectedSwitchId = null },
                onRefresh = { viewModel.refreshNow() }
            )
        }
    }

    // Filtering devices based on selected tower and search query
    val filteredDevices = remember(savedDevices, uiState.selectedFolderId, uiState.searchQuery, selectedCategory) {
        savedDevices.filter { dev ->
            val isSwitch = dev.deviceType == DeviceType.SWITCH || dev.name.contains("switch", ignoreCase = true) || dev.model.contains("CRS", ignoreCase = true) || dev.model.contains("switch", ignoreCase = true)
            val matchesCategory = if (selectedCategory) isSwitch else !isSwitch
            val matchesFolder = when (uiState.selectedFolderId) {
                null -> true
                DeviceFolderEntity.DEFAULT_FOLDER_ID -> dev.folderId.isBlank() || dev.folderId == DeviceFolderEntity.DEFAULT_FOLDER_ID
                else -> dev.folderId == uiState.selectedFolderId
            }
            val matchesQuery = uiState.searchQuery.isBlank() ||
                    dev.name.contains(uiState.searchQuery, ignoreCase = true) ||
                    dev.ipAddress.contains(uiState.searchQuery)
            matchesCategory && matchesFolder && matchesQuery
        }.sortedWith { first, second ->
            compareIpv4Addresses(first.ipAddress, second.ipAddress)
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

        item {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(13.dp)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(if (isArabic) "السكترات والأجهزة" else "Sectors & devices", if (isArabic) "السويجات" else "Switches").forEachIndexed { index, label ->
                    val selected = selectedCategory == (index == 1)
                    Surface(
                        modifier = Modifier.weight(1f).clickable { selectedCategory = index == 1 },
                        color = if (selected) TealAccent.copy(alpha = 0.16f) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(label, modifier = Modifier.padding(vertical = 11.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = if (selected) TealAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal, fontSize = 13.sp)
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
        // Tower Selector - compact selectable cards
        // -------------------------------------------------------------
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CellTower, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isArabic) "اختيار البرج" else "Select Tower",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (isArabic) "${savedFolders.count { it.id != DeviceFolderEntity.DEFAULT_FOLDER_ID }} برج" else "${savedFolders.count { it.id != DeviceFolderEntity.DEFAULT_FOLDER_ID }} towers",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                val towerOptions: List<DeviceFolder?> = listOf(null) + savedFolders
                towerOptions.chunked(2).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        rowOptions.forEach { folder ->
                            val isAll = folder == null
                            val isUnassigned = folder?.id == DeviceFolderEntity.DEFAULT_FOLDER_ID
                            val selected = if (isAll) uiState.selectedFolderId == null else uiState.selectedFolderId == folder?.id
                            val title = when {
                                isAll -> if (isArabic) "جميع الأبراج" else "All Towers"
                                isUnassigned -> if (isArabic) "أجهزة غير تابعة" else "Unassigned"
                                else -> folder?.name.orEmpty()
                            }
                            val count = when {
                                isAll -> savedDevices.size
                                isUnassigned -> savedDevices.count { it.folderId.isBlank() || it.folderId == DeviceFolderEntity.DEFAULT_FOLDER_ID }
                                else -> folder?.deviceCount ?: 0
                            }
                            val icon = when {
                                isAll -> Icons.Default.CellTower
                                isUnassigned -> Icons.Default.FolderOpen
                                else -> Icons.Default.Folder
                            }
                            Card(
                                modifier = Modifier.weight(1f).heightIn(min = 82.dp)
                                    .clickable {
                                        if (isAll) viewModel.selectFolder(null) else folder?.let { viewModel.selectFolder(it.id) }
                                    }
                                    .border(
                                        width = if (selected) 1.4.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = RoundedCornerShape(13.dp)
                                    ),
                                shape = RoundedCornerShape(13.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        if (selected) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        } else {
                                            Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                        }
                                    }
                                    Text(
                                        title,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 12.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        if (isAll) (if (isArabic) "كل الأجهزة" else "All devices")
                                        else if (isUnassigned) (if (isArabic) "بدون برج" else "No tower")
                                        else if (isArabic) "$count جهاز" else "$count devices",
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        if (rowOptions.size == 1) Spacer(Modifier.weight(1f))
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
                    snmpInfo = devSnmp,
                    isArabic = isArabic,
                    onClick = { viewModel.selectDevice(device.id) },
                    onViewDetails = { if (device.deviceType == DeviceType.SWITCH || device.name.contains("switch", true) || device.model.contains("CRS", true) || device.model.contains("switch", true)) selectedSwitchId = device.id else onViewDeviceDetails(device.id) }
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
    snmpInfo: com.netvision.app.monitoring.SnmpDeviceInfo?,
    isArabic: Boolean,
    onClick: () -> Unit,
    onViewDetails: () -> Unit
) {
    val context = LocalContext.current

    val isOnline = device.status == DeviceStatus.ONLINE
    val statusColor = if (isOnline) StatusGreen else StatusRed
    val hasAirMaxSectorData = snmpInfo?.let {
        it.ubntNoiseFloorDbm != null ||
            it.ubntTransmitCcqPercent != null ||
            it.ubntAirMaxQualityPercent != null ||
            it.ubntAirMaxCapacityPercent != null ||
            it.ubntApMac != null
    } == true
    val isUbiquitiSector = hasAirMaxSectorData ||
        device.manufacturer == Manufacturer.UBIQUITI ||
        snmpInfo?.model?.contains("Ubiquiti", ignoreCase = true) == true ||
        snmpInfo?.sysDescr?.let { it.contains("AirOS", true) || it.contains("UBNT", true) } == true
    val isMikroTikSector = device.manufacturer == Manufacturer.MIKROTIK ||
        snmpInfo?.mikrotikCcqPercent != null ||
        snmpInfo?.mikrotikVoltageDecivolts != null
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val metricPalette = if (isDark) {
        listOf(
            Color(0xFF123B4A) to Color(0xFF8BE9F5),
            Color(0xFF173D2B) to Color(0xFF86EFAC),
            Color(0xFF49351A) to Color(0xFFFCD28D),
            Color(0xFF1D3558) to Color(0xFF93C5FD),
            Color(0xFF48233C) to Color(0xFFF9A8D4)
        )
    } else {
        listOf(
            Color(0xFFE0F7FA) to Color(0xFF0E7490),
            Color(0xFFDCFCE7) to Color(0xFF15803D),
            Color(0xFFFFF2D9) to Color(0xFFB7791F),
            Color(0xFFE5F0FF) to Color(0xFF2563EB),
            Color(0xFFFCE7F3) to Color(0xFFBE185D)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) TealAccent else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Surface(color = statusColor.copy(alpha = 0.13f), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = if (isOnline) (if (isArabic) "متصل" else "Online") else (if (isArabic) "غير متصل" else "Offline"),
                        color = statusColor,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "IP: ${device.ipAddress}",
                    color = TealAccent,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        val address = device.ipAddress.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$address"))
                        context.startActivity(intent)
                    }
                )
                Surface(
                    color = (if (isSnmpActive) StatusGreen else StatusAmber).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(7.dp)
                ) {
                    Text(
                        if (isSnmpActive) "SNMP Active" else "SNMP Unavailable",
                        color = if (isSnmpActive) StatusGreen else StatusAmber,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if ((isUbiquitiSector || isMikroTikSector) && snmpInfo?.isSuccess == true) {
                val values = if (isMikroTikSector && !isUbiquitiSector) {
                    listOf(
                        Triple(if (isArabic) "الأجهزة" else "Clients", snmpInfo.connectedClientsCount?.toString() ?: "—", 0),
                        Triple("CCQ", snmpInfo.mikrotikCcqPercent?.let { "${it}%" } ?: "—", 1),
                        Triple(if (isArabic) "الفولتية" else "Voltage", snmpInfo.mikrotikVoltageDecivolts?.let { String.format(Locale.US, "%.1f V", it / 10.0) } ?: "—", 2)
                    )
                } else {
                    listOf(
                        Triple(if (isArabic) "الأجهزة" else "Clients", snmpInfo.connectedClientsCount?.toString() ?: "—", 0),
                        Triple("CCQ", snmpInfo.ubntTransmitCcqPercent?.let { "${it}%" } ?: "—", 1),
                        Triple(if (isArabic) "Noise Floor" else "Noise Floor", snmpInfo.ubntNoiseFloorDbm?.let { "${it} dBm" } ?: "—", 2),
                        Triple("airMAX Quality", snmpInfo.ubntAirMaxQualityPercent?.let { "${it}%" } ?: "—", 3),
                        Triple("airMAX Capacity", snmpInfo.ubntAirMaxCapacityPercent?.let { "${it}%" } ?: "—", 4)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (isArabic) "معلومات السكتر" else "Sector information",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                    values.chunked(3).forEach { rowValues ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowValues.forEach { (label, value, colorIndex) ->
                                val colors = metricPalette[colorIndex]
                                Column(
                                    modifier = Modifier.weight(1f)
                                        .background(colors.first, RoundedCornerShape(9.dp))
                                        .padding(horizontal = 7.dp, vertical = 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(label, fontSize = 9.sp, color = colors.second, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(value, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = colors.second, maxLines = 1)
                                }
                            }
                            if (rowValues.size < 3) repeat(3 - rowValues.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    val durationBackground = if (isDark) Color(0xFF34264F) else Color(0xFFEEE8FF)
                    val durationForeground = if (isDark) Color(0xFFD8C6FF) else Color(0xFF6542A6)
                    Row(
                        Modifier.fillMaxWidth().background(durationBackground, RoundedCornerShape(9.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isArabic) "مدة تشغيل السكتر" else "Sector uptime", color = durationForeground, fontSize = 11.sp)
                        Text(snmpInfo.uptimeFormatted ?: "—", color = durationForeground, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = onViewDetails,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Text(if (device.deviceType == DeviceType.SWITCH || device.name.contains("switch", true) || device.model.contains("CRS", true) || device.model.contains("switch", true)) { if (isArabic) "عرض منافذ السويج" else "View switch ports" } else { if (isArabic) "عرض المحطات المتصلة" else "View connected stations" }, fontSize = 12.sp)
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

@Composable
private fun SwitchPortsDetailPage(
    device: NetworkDevice,
    snapshot: EthernetMonitoringSnapshot?,
    isArabic: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    var expandedIndex by remember(device.id) { mutableStateOf<Int?>(null) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = if (isArabic) "رجوع" else "Back") }
                Text(if (isArabic) "منافذ السويج" else "Switch ports", style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = if (isArabic) "تحديث" else "Refresh") }
            }
            Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            Text("IP: ${device.ipAddress}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (isArabic) "المنافذ الفيزيائية" else "Physical ports", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("${snapshot?.connectedCount ?: 0}/${snapshot?.totalCount ?: 0} ${if (isArabic) "متصل" else "connected"}", color = StatusGreen)
            }
            Spacer(Modifier.height(10.dp))
            if (snapshot == null || snapshot.ports.isEmpty()) {
                Text(snapshot?.error ?: if (isArabic) "بانتظار بيانات SNMP للمنافذ" else "Waiting for SNMP port data", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(snapshot.ports, key = { it.index }) { port ->
                        val connected = port.operStatus == 1
                        val accent = if (connected) StatusGreen else StatusRed
                        val bg = accent.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 0.16f else 0.09f)
                        Column(Modifier.fillMaxWidth().background(bg, RoundedCornerShape(12.dp)).clickable { expandedIndex = if (expandedIndex == port.index) null else port.index }.padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(port.name, color = MaterialTheme.colorScheme.onSurface, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Box(Modifier.size(10.dp).background(if (connected) StatusGreen else StatusRed, CircleShape))
                                    Text("●", color = if (connected) StatusGreen else StatusRed, fontSize = 16.sp)
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (port.speedBps != null && port.speedBps > 0) if (port.speedBps >= 1_000_000_000L) String.format(Locale.US, "%.1f Gbps", port.speedBps / 1_000_000_000.0) else "${port.speedBps / 1_000_000} Mbps" else "Rate: —", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                Text("TX ${port.txMbps?.let { String.format(Locale.US, "%.2f", it) } ?: "—"} Mbps", color = Color(0xFF3B82F6), fontSize = 11.sp)
                                Text("RX ${port.rxMbps?.let { String.format(Locale.US, "%.2f", it) } ?: "—"} Mbps", color = Color(0xFF06B6D4), fontSize = 11.sp)
                            }
                            if (expandedIndex == port.index) {
                                HorizontalDivider(color = accent.copy(alpha = 0.3f))
                                Text("TX total: ${port.txOctets?.let { String.format(Locale.US, "%.2f GB", it / 1_000_000_000.0) } ?: "—"}", fontSize = 11.sp)
                                Text("RX total: ${port.rxOctets?.let { String.format(Locale.US, "%.2f GB", it / 1_000_000_000.0) } ?: "—"}", fontSize = 11.sp)
                                Text(if (isArabic) "مرات انقطاع الرابط: غير متاح حالياً" else "Link downs: unavailable", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

