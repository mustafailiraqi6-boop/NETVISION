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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netvision.app.database.DeviceFolderEntity
import com.netvision.app.models.DeviceFolder
import com.netvision.app.models.DeviceType
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.NetworkRange
import com.netvision.app.ui.theme.*
import com.netvision.app.ui.viewmodels.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    isArabic: Boolean,
    modifier: Modifier = Modifier,
    viewModel: ScannerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val savedRanges by viewModel.savedRanges.collectAsState()
    val savedDevices by viewModel.savedDevices.collectAsState()
    val savedFolders by viewModel.savedFolders.collectAsState()

    var newCidrInput by remember { mutableStateOf("") }
    var newLabelInput by remember { mutableStateOf("") }
    var showAddRangeDialog by remember { mutableStateOf(false) }

    // Dialog State for adding device to monitoring
    var deviceToAdd by remember { mutableStateOf<NetworkDevice?>(null) }
    var duplicateDeviceWarning by remember { mutableStateOf<Pair<NetworkDevice, String>?>(null) }

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
                    text = if (isArabic) "فاحص الشبكات واكتشاف الأجهزة" else "Network Scanner & Discovery",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isArabic) "فحص نطاقات CIDR واكتشاف أجهزة Ubiquiti و MikroTik عبر اتصال OpenVPN"
                    else "Scan CIDR subnets & discover real Ubiquiti and MikroTik hardware over OpenVPN",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Error message card
        if (uiState.errorMessage != null) {
            item {
                Surface(
                    color = StatusRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusRed)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.errorMessage ?: "",
                            fontSize = 12.sp,
                            color = StatusRed,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = StatusRed)
                        }
                    }
                }
            }
        }

        // Subnet CIDR Ranges Management Card
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
                            text = if (isArabic) "نطاقات الشبكة CIDR المحفوظة" else "Configured CIDR Subnets",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(onClick = { showAddRangeDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyanAccent)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isArabic) "إضافة نطاق" else "Add Subnet", color = CyanAccent)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (savedRanges.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isArabic) "لا توجد نطاقات CIDR محفوظة حالياً." else "No CIDR subnets configured yet.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isArabic) "أمثلة: 192.168.1.0/24 أو 10.197.56.224/27 أو 10.10.10.0/24"
                                    else "Examples: 192.168.1.0/24, 10.197.56.224/27, 10.10.10.0/24",
                                    fontSize = 11.sp,
                                    color = CyanAccent
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            savedRanges.forEach { range ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Checkbox(
                                            checked = range.isEnabled,
                                            onCheckedChange = { viewModel.toggleRange(range.id, it) }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column {
                                            Text(text = range.label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                            Text(text = range.cidr, fontSize = 11.sp, color = CyanAccent)
                                        }
                                    }

                                    IconButton(onClick = { viewModel.deleteRange(range.id) }) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = StatusRed.copy(alpha = 0.8f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Scan Input & Action Controls
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (uiState.isScanning) (if (isArabic) "جاري الفحص المباشر عبر VPN..." else "Live Probing in Progress...")
                                else (if (isArabic) "حالة الفاحص: جاهز" else "Scanner Status: Ready"),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (uiState.isScanning && uiState.currentScannedIp.isNotEmpty()) {
                                Text(
                                    text = if (isArabic) "فحص الهدف: ${uiState.currentScannedIp}" else "Probing: ${uiState.currentScannedIp}",
                                    fontSize = 12.sp,
                                    color = CyanAccent
                                )
                            }
                        }

                        if (!uiState.isScanning) {
                            Button(
                                onClick = { viewModel.startScan() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = NavyDark),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isArabic) "بدء الفحص" else "Start Scan")
                            }
                        } else {
                            Button(
                                onClick = { viewModel.stopScan() },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusRed, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isArabic) "إيقاف الفحص" else "Stop Scan")
                            }
                        }
                    }

                    if (uiState.isScanning || uiState.progress > 0f) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { uiState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = CyanAccent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${(uiState.progress * 100).toInt()}% (${uiState.scannedHostsCount} / ${uiState.totalHostsCount})",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isArabic) "${uiState.discoveredDevices.size} جهاز تم اكتشافه فعلياً"
                                else "${uiState.discoveredDevices.size} Discovered",
                                fontSize = 11.sp,
                                color = CyanAccent
                            )
                        }
                    }
                }
            }
        }

        // Discovered Devices Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) "نتائج الاكتشاف الحقيقية (${uiState.discoveredDevices.size})"
                    else "Discovered Hardware (${uiState.discoveredDevices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Empty state or Discovered Device Cards
        if (uiState.discoveredDevices.isEmpty()) {
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
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (uiState.progress >= 1f) {
                                if (isArabic) "لم يتم العثور على أجهزة تستجيب في النطاق المحدد.\nتأكد من اتصال OpenVPN وأن مسارات الشبكة متاحة."
                                else "No responding devices discovered in target CIDR range.\nVerify OpenVPN connection and route access."
                            } else {
                                if (isArabic) "حدد نطاق CIDR واضغط على 'بدء الفحص' لاكتشاف أجهزة UBNT و MikroTik الحقيقية."
                                else "Select CIDR subnet and tap 'Start Scan' to discover genuine devices."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(uiState.discoveredDevices, key = { it.id }) { dev ->
                val existingDevice = savedDevices.find { it.ipAddress == dev.ipAddress || it.id == dev.id }
                val isAlreadyAdded = existingDevice != null || uiState.addedDeviceIds.contains(dev.id) || uiState.addedDeviceIds.contains(dev.ipAddress)

                DiscoveredDeviceCard(
                    device = dev,
                    isArabic = isArabic,
                    isAdded = isAlreadyAdded,
                    onAddClick = {
                        if (existingDevice != null) {
                            val towerName = savedFolders.find { it.id == existingDevice.folderId }?.name
                                ?: if (existingDevice.folderId == DeviceFolderEntity.DEFAULT_FOLDER_ID) {
                                    if (isArabic) "أجهزة غير تابعة لبرج" else "Unassigned Devices"
                                } else existingDevice.folderId
                            duplicateDeviceWarning = Pair(dev, towerName)
                        } else {
                            deviceToAdd = dev
                        }
                    }
                )
            }
        }
    }

    // -------------------------------------------------------------
    // Add Device to Tower Modal Dialog (إضافة الجهاز للمراقبة)
    // -------------------------------------------------------------
    if (deviceToAdd != null) {
        val target = deviceToAdd!!
        var selectedFolderId by remember(target.id) { mutableStateOf(DeviceFolderEntity.DEFAULT_FOLDER_ID) }
        var selectedDeviceType by remember(target.id) { mutableStateOf<DeviceType?>(null) }

        AlertDialog(
            onDismissRequest = { deviceToAdd = null },
            title = {
                Text(text = if (isArabic) "إضافة الجهاز للمراقبة" else "Add Device to Monitoring")
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${target.name} (${target.ipAddress})",
                        style = MaterialTheme.typography.titleMedium,
                        color = CyanAccent
                    )
                    Text(
                        text = "${target.manufacturer.displayName} • ${target.model}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isArabic) "ما نوع الجهاز؟" else "Device Type",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            DeviceType.SWITCH,
                            DeviceType.SECTOR_AP,
                            DeviceType.ROUTER
                        ).forEach { type ->
                            FilterChip(
                                modifier = Modifier.weight(1f),
                                selected = selectedDeviceType == type,
                                onClick = { selectedDeviceType = type },
                                label = {
                                    Text(
                                        text = when (type) {
                                            DeviceType.SWITCH -> if (isArabic) "سويج" else "Switch"
                                            DeviceType.SECTOR_AP -> if (isArabic) "سكتر" else "Sector"
                                            else -> if (isArabic) "راوتر" else "Router"
                                        },
                                        fontSize = 11.sp
                                    )
                                }
                            )
                        }
                    }

                    Text(
                        text = if (isArabic) "اختر البرج التابع له الجهاز:" else "Select Assigned Tower:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(savedFolders) { folder ->
                            val isUnassigned = folder.id == DeviceFolderEntity.DEFAULT_FOLDER_ID
                            val folderLabel = if (isUnassigned) {
                                if (isArabic) "أجهزة غير تابعة لبرج" else "Unassigned Devices"
                            } else {
                                folder.name
                            }

                            FilterChip(
                                selected = selectedFolderId == folder.id,
                                onClick = { selectedFolderId = folder.id },
                                label = { Text(folderLabel, fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isUnassigned) Icons.Default.FolderOpen else Icons.Default.CellTower,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addDeviceToMonitoring(target, selectedFolderId)
                        deviceToAdd = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = NavyDark)
                ) {
                    Text(if (isArabic) "حفظ وإضافة للمراقبة" else "Save & Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToAdd = null }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    // -------------------------------------------------------------
    // Duplicate Device Alert Warning Dialog
    // -------------------------------------------------------------
    if (duplicateDeviceWarning != null) {
        val (dupDev, dupTower) = duplicateDeviceWarning!!
        AlertDialog(
            onDismissRequest = { duplicateDeviceWarning = null },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusAmber)
            },
            title = {
                Text(text = if (isArabic) "الجهاز مضاف بالفعل" else "Device Already Monitored")
            },
            text = {
                Text(
                    text = if (isArabic) "الجهاز '${dupDev.name}' (${dupDev.ipAddress}) موجود بالفعل في قائمة المراقبة تحت [${dupTower}]."
                    else "The device '${dupDev.name}' (${dupDev.ipAddress}) is already present in monitoring under [${dupTower}]."
                )
            },
            confirmButton = {
                Button(
                    onClick = { duplicateDeviceWarning = null },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = NavyDark)
                ) {
                    Text(if (isArabic) "موافق" else "OK")
                }
            }
        )
    }

    // Add Range Dialog
    if (showAddRangeDialog) {
        AlertDialog(
            onDismissRequest = { showAddRangeDialog = false },
            title = {
                Text(text = if (isArabic) "إضافة نطاق شبكة جديد (CIDR)" else "Add New CIDR Subnet")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newCidrInput,
                        onValueChange = { newCidrInput = it },
                        label = { Text(if (isArabic) "نطاق CIDR (مثال: 10.197.56.224/27)" else "CIDR (e.g. 10.197.56.224/27)") },
                        placeholder = { Text("192.168.1.0/24") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newLabelInput,
                        onValueChange = { newLabelInput = it },
                        label = { Text(if (isArabic) "اسم أو وصف النطاق (اختياري)" else "Subnet Label (Optional)") },
                        placeholder = { Text(if (isArabic) "شبكة الأجهزة الرئيسية" else "Core Routers Subnet") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCidrInput.isNotBlank()) {
                            viewModel.addRange(newCidrInput, newLabelInput)
                            newCidrInput = ""
                            newLabelInput = ""
                            showAddRangeDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = NavyDark)
                ) {
                    Text(if (isArabic) "حفظ النطاق" else "Save Subnet")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddRangeDialog = false }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }
}

@Composable
fun DiscoveredDeviceCard(
    device: NetworkDevice,
    isArabic: Boolean,
    isAdded: Boolean,
    onAddClick: () -> Unit
) {
    val vendorColor = when (device.manufacturer) {
        Manufacturer.UBIQUITI -> ElectricBlue
        Manufacturer.MIKROTIK -> CyanAccent
        Manufacturer.CISCO -> Color(0xFF38BDF8)
        Manufacturer.OTHER -> Color(0xFFA78BFA)
        Manufacturer.GENERIC -> TextMutedDark
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Name & Vendor Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(StatusGreen)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = vendorColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = device.manufacturer.displayName,
                        fontSize = 11.sp,
                        color = vendorColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Details: Model & Type & Latency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = device.model, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "•", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (isArabic) device.deviceType.labelAr else device.deviceType.labelEn,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(text = "•", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${device.metrics.pingLatencyMs} ms",
                    fontSize = 12.sp,
                    color = CyanAccent
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // IP & MAC Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "IP: ${device.ipAddress}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "MAC: ${device.macAddress}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (isAdded) {
                    FilledTonalButton(
                        onClick = onAddClick,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = StatusGreen)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isArabic) "مضاف للمراقبة" else "Monitored", fontSize = 12.sp, color = StatusGreen)
                    }
                } else {
                    Button(
                        onClick = onAddClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = NavyDark),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isArabic) "إضافة للمراقبة" else "Add to Monitoring", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
