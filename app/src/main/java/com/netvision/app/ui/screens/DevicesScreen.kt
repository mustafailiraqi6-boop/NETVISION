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
import com.netvision.app.database.DeviceFolderEntity
import com.netvision.app.models.DeviceFolder
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

    // Dialog States
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var deviceToEdit by remember { mutableStateOf<NetworkDevice?>(null) }
    var deviceToDelete by remember { mutableStateOf<NetworkDevice?>(null) }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToEdit by remember { mutableStateOf<DeviceFolder?>(null) }
    var folderToDelete by remember { mutableStateOf<DeviceFolder?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Screen Header with Action Buttons
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isArabic) "أجهزة المراقبة المحفوظة" else "Monitored Devices",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isArabic) "إجمالي الأجهزة: ${uiState.totalCount} • الحافظات: ${uiState.folders.size}"
                            else "Total Devices: ${uiState.totalCount} • Folders: ${uiState.folders.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = { showCreateFolderDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isArabic) "+ حافظة" else "+ Folder", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { showAddDeviceDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = NavyDark),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isArabic) "+ جهاز" else "+ Device", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section 1: Device Folders System (نظام الحافظات)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isArabic) "حافظات الأبراج والمواقع" else "Tower & Location Folders",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = if (uiState.selectedFolderId == null) {
                                if (isArabic) "عرض الكل" else "Viewing All"
                            } else {
                                val current = uiState.folders.find { it.id == uiState.selectedFolderId }
                                current?.name ?: ""
                            },
                            fontSize = 11.sp,
                            color = CyanAccent
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // "Show All Devices" Option
                        item {
                            FilterChip(
                                selected = uiState.selectedFolderId == null,
                                onClick = { viewModel.selectFolder(null) },
                                label = {
                                    Text(
                                        text = "${if (isArabic) "جميع الأجهزة" else "All Devices"} (${uiState.totalCount})",
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FolderShared, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }

                        // Each Folder with device count badge
                        items(uiState.folders) { folder ->
                            val isSelected = uiState.selectedFolderId == folder.id
                            val isDefault = folder.id == DeviceFolderEntity.DEFAULT_FOLDER_ID

                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectFolder(folder.id) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("${folder.name} (${folder.deviceCount})", fontSize = 12.sp)
                                        if (!isDefault) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit Folder",
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clickable { folderToEdit = folder },
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Delete Folder",
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clickable { folderToDelete = folder },
                                                tint = StatusRed.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isDefault) Icons.Default.FolderOpen else Icons.Default.Folder,
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

        // Search Field
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
                shape = RoundedCornerShape(12.dp)
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
                        selected = uiState.selectedManufacturer == Manufacturer.OTHER,
                        onClick = {
                            viewModel.onManufacturerSelected(
                                if (uiState.selectedManufacturer == Manufacturer.OTHER) null else Manufacturer.OTHER
                            )
                        },
                        label = { Text(if (isArabic) "أخرى" else "Other") }
                    )
                }
            }
        }

        // Empty state
        if (uiState.devices.isEmpty()) {
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
                            text = if (isArabic) "لا توجد أجهزة مطابقة في هذه الحافظة."
                            else "No matching devices found in this folder.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isArabic) "يمكنك إضافة جهاز يدوياً بالضغط على زر '+ جهاز' أو إجراء فحص تلقائي عبر فاحص الشبكة."
                            else "Add a device manually via '+ Device' or run automatic discovery in Network Scanner.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            // Devices List
            items(uiState.devices, key = { it.id }) { device ->
                val folderName = uiState.folders.find { it.id == device.folderId }?.name
                    ?: (if (device.folderId == DeviceFolderEntity.DEFAULT_FOLDER_ID) "غير مصنف" else device.folderId)

                SavedDeviceCardItem(
                    device = device,
                    folderName = folderName,
                    isArabic = isArabic,
                    onEditClick = { deviceToEdit = device },
                    onDeleteClick = { deviceToDelete = device }
                )
            }
        }
    }

    // -------------------------------------------------------------
    // Add / Edit Device Dialog (نافذة إضافة وتعديل الجهاز المطوّرة)
    // -------------------------------------------------------------
    if (showAddDeviceDialog || deviceToEdit != null) {
        val isEditing = deviceToEdit != null
        val existing = deviceToEdit

        var nameInput by remember { mutableStateOf(existing?.name ?: "") }
        var ipInput by remember { mutableStateOf(existing?.ipAddress ?: "") }
        var selectedVendor by remember { mutableStateOf(existing?.manufacturer ?: Manufacturer.MIKROTIK) }
        var selectedType by remember { mutableStateOf(existing?.deviceType ?: DeviceType.ROUTER) }
        var selectedFolder by remember {
            mutableStateOf(existing?.folderId ?: uiState.selectedFolderId ?: DeviceFolderEntity.DEFAULT_FOLDER_ID)
        }

        AlertDialog(
            onDismissRequest = {
                showAddDeviceDialog = false
                deviceToEdit = null
            },
            title = {
                Text(
                    text = if (isEditing) {
                        if (isArabic) "تعديل بيانات الجهاز" else "Edit Device Details"
                    } else {
                        if (isArabic) "إضافة جهاز مراقبة جديد" else "Add New Monitored Device"
                    }
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Field 1: Device Name
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isArabic) "اسم الجهاز" else "Device Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Field 2: IP Address
                    OutlinedTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isArabic) "عنوان IP" else "IP Address") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Field 3: Manufacturer (الشركة المصنعة)
                    Text(
                        text = if (isArabic) "الشركة المصنعة:" else "Manufacturer:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(Manufacturer.MIKROTIK, Manufacturer.UBIQUITI, Manufacturer.OTHER).forEach { vendor ->
                            FilterChip(
                                selected = selectedVendor == vendor,
                                onClick = { selectedVendor = vendor },
                                label = { Text(if (vendor == Manufacturer.OTHER) (if (isArabic) "أخرى" else "Other") else vendor.displayName, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Field 4: Device Type (نوع الجهاز)
                    Text(
                        text = if (isArabic) "نوع الجهاز:" else "Device Type:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(listOf(DeviceType.ROUTER, DeviceType.SECTOR_AP, DeviceType.SWITCH, DeviceType.CLIENT_DEVICE, DeviceType.OTHER)) { type ->
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = type },
                                label = { Text(if (isArabic) type.labelAr else type.labelEn, fontSize = 11.sp) }
                            )
                        }
                    }

                    // Field 5: Assigned Folder (الحافظة التابعة له)
                    Text(
                        text = if (isArabic) "الحافظة التابعة له (البرج / الموقع):" else "Assigned Folder (Tower/Site):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(uiState.folders) { folder ->
                            FilterChip(
                                selected = selectedFolder == folder.id,
                                onClick = { selectedFolder = folder.id },
                                label = { Text(folder.name, fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isEditing && existing != null) {
                            viewModel.updateDeviceFull(
                                deviceId = existing.id,
                                name = nameInput.ifBlank { existing.name },
                                ipAddress = ipInput.ifBlank { existing.ipAddress },
                                manufacturer = selectedVendor,
                                deviceType = selectedType,
                                folderId = selectedFolder
                            )
                        } else {
                            viewModel.addDevice(
                                name = nameInput.ifBlank { "Device $ipInput" },
                                ipAddress = ipInput,
                                manufacturer = selectedVendor,
                                deviceType = selectedType,
                                folderId = selectedFolder
                            )
                        }
                        showAddDeviceDialog = false
                        deviceToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = NavyDark)
                ) {
                    Text(if (isEditing) (if (isArabic) "حفظ التغييرات" else "Save Changes") else (if (isArabic) "إضافة الجهاز" else "Add Device"))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDeviceDialog = false
                    deviceToEdit = null
                }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    // -------------------------------------------------------------
    // Create Folder Dialog (إنشاء حافظة جديدة)
    // -------------------------------------------------------------
    if (showCreateFolderDialog) {
        var folderNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(if (isArabic) "إنشاء حافظة جديدة" else "Create New Folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isArabic) "أدخل اسم البرج أو الموقع لتنظيم أجهزة الشبكة داخله (مثلاً: برج داقوق، برج المركز):"
                        else "Enter the tower or location name to organize network devices (e.g. Tower North, Site Central):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = folderNameInput,
                        onValueChange = { folderNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isArabic) "اسم الحافظة / البرج" else "Folder / Tower Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameInput.isNotBlank()) {
                            viewModel.createFolder(folderNameInput.trim())
                            showCreateFolderDialog = false
                        }
                    },
                    enabled = folderNameInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = NavyDark)
                ) {
                    Text(if (isArabic) "إنشاء" else "Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    // -------------------------------------------------------------
    // Edit Folder Dialog (تعديل اسم الحافظة)
    // -------------------------------------------------------------
    if (folderToEdit != null) {
        val target = folderToEdit!!
        var editName by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { folderToEdit = null },
            title = { Text(if (isArabic) "تعديل اسم الحافظة" else "Rename Folder") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isArabic) "الاسم الجديد للحافظة" else "New Folder Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank()) {
                            viewModel.updateFolderName(target.id, editName.trim())
                            folderToEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = NavyDark)
                ) {
                    Text(if (isArabic) "تحديث" else "Update")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToEdit = null }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    // -------------------------------------------------------------
    // Delete Folder Dialog (حذف الحافظة مع حماية الأجهزة)
    // -------------------------------------------------------------
    if (folderToDelete != null) {
        val target = folderToDelete!!
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(if (isArabic) "حذف الحافظة" else "Delete Folder") },
            text = {
                Text(
                    text = if (isArabic) "هل أنت متأكد من حذف الحافظة '${target.name}'؟\n\nتنبيه أمان: لن يتم حذف أي جهاز نهائياً، وسيتم نقل جميع الأجهزة التابعة لهذه الحافظة (${target.deviceCount} جهاز) تلقائياً إلى حافظة 'غير مصنف' لحمايتها."
                    else "Are you sure you want to delete '${target.name}'?\n\nSafety Note: No devices will be deleted. All ${target.deviceCount} devices in this folder will be safely moved to 'Uncategorized'."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFolder(target.id)
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRed, contentColor = Color.White)
                ) {
                    Text(if (isArabic) "تأكيد الحذف والنقل" else "Confirm & Move")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    // -------------------------------------------------------------
    // Delete Device Dialog
    // -------------------------------------------------------------
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
    folderName: String,
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

            // Subtitle: Manufacturer & Model & Type & Folder Badge
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

            Spacer(modifier = Modifier.height(6.dp))

            // Folder Indicator Badge
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = CyanAccent
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = folderName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
