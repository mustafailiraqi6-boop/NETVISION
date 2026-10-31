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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.DeviceType
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.ui.theme.*
import com.netvision.app.ui.viewmodels.DevicesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    isArabic: Boolean,
    modifier: Modifier = Modifier,
    viewModel: DevicesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var deviceToEdit by remember { mutableStateOf<NetworkDevice?>(null) }
    var deviceToDelete by remember { mutableStateOf<NetworkDevice?>(null) }

    var editNameInput by remember { mutableStateOf("") }
    var editTypeSelection by remember { mutableStateOf(DeviceType.ROUTER) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Screen Header
        item {
            Column {
                Text(
                    text = if (isArabic) "أجهزة المراقبة المحفوظة" else "Monitored Devices",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isArabic) "إجمالي الأجهزة المحفوظة في قاعدة البيانات: ${uiState.totalCount}"
                    else "Saved in local Room Database: ${uiState.totalCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Search Field (shown if there are devices)
        if (uiState.totalCount > 0) {
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = if (isArabic) "بحث بالاسم، IP، MAC أو الموديل..." else "Search by name, IP, MAC or model...",
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = CyanAccent)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }

            // Filter Chips (Manufacturer)
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedManufacturer == null,
                            onClick = { viewModel.onManufacturerSelected(null) },
                            label = { Text(if (isArabic) "جميع المصنعين" else "All Vendors") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.selectedManufacturer == Manufacturer.UBIQUITI,
                            onClick = {
                                viewModel.onManufacturerSelected(
                                    if (uiState.selectedManufacturer == Manufacturer.UBIQUITI) null else Manufacturer.UBIQUITI
                                )
                            },
                            label = { Text("Ubiquiti (UBNT)") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.selectedManufacturer == Manufacturer.MIKROTIK,
                            onClick = {
                                viewModel.onManufacturerSelected(
                                    if (uiState.selectedManufacturer == Manufacturer.MIKROTIK) null else Manufacturer.MIKROTIK
                                )
                            },
                            label = { Text("MikroTik") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.selectedManufacturer == Manufacturer.CISCO,
                            onClick = {
                                viewModel.onManufacturerSelected(
                                    if (uiState.selectedManufacturer == Manufacturer.CISCO) null else Manufacturer.CISCO
                                )
                            },
                            label = { Text("Cisco") }
                        )
                    }
                }
            }
        }

        // Empty state when DB is empty
        if (uiState.totalCount == 0) {
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
                            imageVector = Icons.Default.DevicesOther,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isArabic) "لا توجد أجهزة مضافة للمراقبة حالياً."
                            else "No devices saved for monitoring yet.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isArabic) "انتقل إلى قسم 'فاحص الشبكة' لتحديد نطاق CIDR، واكتشاف أجهزة UBNT و MikroTik وإضافتها هنا للمراقبة الدائمة."
                            else "Go to the Network Scanner tab to probe your CIDR subnets and add real devices for permanent monitoring.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else if (uiState.devices.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = if (isArabic) "لا توجد أجهزة مطابقة لمعايير البحث الحالية."
                        else "No devices matched your search or filter.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            // Device Items List
            items(uiState.devices, key = { it.id }) { device ->
                SavedDeviceCardItem(
                    device = device,
                    isArabic = isArabic,
                    onEditClick = {
                        deviceToEdit = device
                        editNameInput = device.name
                        editTypeSelection = device.deviceType
                    },
                    onDeleteClick = {
                        deviceToDelete = device
                    }
                )
            }
        }
    }

    // Edit Device Dialog
    if (deviceToEdit != null) {
        val target = deviceToEdit!!
        AlertDialog(
            onDismissRequest = { deviceToEdit = null },
            title = { Text(if (isArabic) "تعديل بيانات الجهاز" else "Edit Device Details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editNameInput,
                        onValueChange = { editNameInput = it },
                        label = { Text(if (isArabic) "اسم الجهاز" else "Device Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = if (isArabic) "نوع الجهاز:" else "Device Type:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(DeviceType.ROUTER, DeviceType.SWITCH, DeviceType.ACCESS_POINT).forEach { type ->
                            FilterChip(
                                selected = editTypeSelection == type,
                                onClick = { editTypeSelection = type },
                                label = { Text(if (isArabic) type.labelAr else type.labelEn, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateDevice(target, editNameInput, editTypeSelection)
                        deviceToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = NavyDark)
                ) {
                    Text(if (isArabic) "حفظ التغييرات" else "Save Changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToEdit = null }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    // Delete Device Dialog
    if (deviceToDelete != null) {
        val target = deviceToDelete!!
        AlertDialog(
            onDismissRequest = { deviceToDelete = null },
            title = { Text(if (isArabic) "حذف الجهاز من المراقبة" else "Remove Device from Monitoring") },
            text = {
                Text(
                    text = if (isArabic) "هل أنت متأكد من حذف الجهاز '${target.name}' (${target.ipAddress}) من قائمة المراقبة وقاعدة البيانات؟"
                    else "Are you sure you want to remove '${target.name}' (${target.ipAddress}) from monitoring and local storage?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteDevice(target)
                        deviceToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed, contentColor = Color.White)
                ) {
                    Text(if (isArabic) "تأكيد الحذف" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToDelete = null }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }
}

@Composable
fun SavedDeviceCardItem(
    device: NetworkDevice,
    isArabic: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val statusColor = when (device.status) {
        DeviceStatus.ONLINE -> StatusGreen
        DeviceStatus.OFFLINE -> StatusRed
        DeviceStatus.WARNING -> StatusAmber
    }

    val vendorColor = when (device.manufacturer) {
        Manufacturer.UBIQUITI -> ElectricBlue
        Manufacturer.MIKROTIK -> CyanAccent
        Manufacturer.CISCO -> Color(0xFF38BDF8)
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
            // Header: Name & Status & Actions
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
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isArabic) device.status.labelAr else device.status.labelEn,
                        fontSize = 11.sp,
                        color = statusColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onEditClick, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(16.dp), tint = StatusRed.copy(alpha = 0.8f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle: Manufacturer & Model & Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.manufacturer.displayName,
                    fontSize = 12.sp,
                    color = vendorColor
                )
                Text(text = "•", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = device.model,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(text = "•", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (isArabic) device.deviceType.labelAr else device.deviceType.labelEn,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Network Addresses: IP & MAC & Latency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "IP: ${device.ipAddress}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "MAC: ${device.macAddress}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (device.status == DeviceStatus.ONLINE) {
                        Text(
                            text = "${device.metrics.pingLatencyMs} ms",
                            fontSize = 13.sp,
                            color = CyanAccent
                        )
                    } else {
                        Text(
                            text = if (isArabic) "غير متصل" else "Offline",
                            fontSize = 12.sp,
                            color = StatusRed
                        )
                    }
                    Text(
                        text = if (device.metrics.uptimeFormatted.isNotBlank() && device.metrics.uptimeFormatted != "N/A") "Uptime: ${device.metrics.uptimeFormatted}" else "",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
