package com.netvision.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netvision.app.NetVisionApplication
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.netvision.app.database.DeviceFolderEntity
import com.netvision.app.models.DeviceFolder
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.DeviceType
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.ui.theme.*
import com.netvision.app.ui.viewmodels.DevicesViewModel


private fun selectionCardBackground(index: Int, dark: Boolean): Color = when (index % 5) {
    0 -> if (dark) Color(0xFF263F5A) else Color(0xFFDCEBFA)
    1 -> if (dark) Color(0xFF24483D) else Color(0xFFDDF3E8)
    2 -> if (dark) Color(0xFF463452) else Color(0xFFF0E4F8)
    3 -> if (dark) Color(0xFF51402D) else Color(0xFFFCEBD9)
    else -> if (dark) Color(0xFF4D4A2D) else Color(0xFFF5EFCF)
}
private fun selectionCardBorder(index: Int): Color = when (index % 5) {
    0 -> Color(0xFF83B7E8)
    1 -> Color(0xFF83C9A5)
    2 -> Color(0xFFC4A1E0)
    3 -> Color(0xFFE8BD87)
    else -> Color(0xFFD9CE7E)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    isArabic: Boolean,
    modifier: Modifier = Modifier,
    initialStatus: DeviceStatus? = null,
    viewModel: DevicesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(initialStatus) {
        viewModel.onStatusSelected(initialStatus)
    }

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
                            text = if (isArabic) "الأبراج وأجهزة المراقبة" else "Towers & Monitored Devices",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isArabic) "إجمالي الأجهزة: ${uiState.totalCount} • الأبراج: ${uiState.folders.size}"
                            else "Total Devices: ${uiState.totalCount} • Towers: ${uiState.folders.size}",
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
                            Icon(Icons.Default.CellTower, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isArabic) "+ برج" else "+ Tower", fontSize = 12.sp)
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

        // Section 1: Tower selector - compact two-column cards
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CellTower,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isArabic) "الأبراج والمواقع" else "Towers & Locations",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (isArabic) "${uiState.folders.count { it.id != DeviceFolderEntity.DEFAULT_FOLDER_ID }} برج"
                        else "${uiState.folders.count { it.id != DeviceFolderEntity.DEFAULT_FOLDER_ID }} towers",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val towerOptions = listOf<DeviceFolder?>(null) + uiState.folders
                towerOptions.chunked(2).forEachIndexed { rowIndex, rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEachIndexed { columnIndex, folder ->
                            val selected = if (folder == null) {
                                uiState.selectedFolderId == null
                            } else {
                                uiState.selectedFolderId == folder.id
                            }
                            val isDefault = folder?.id == DeviceFolderEntity.DEFAULT_FOLDER_ID
                            val title = when {
                                folder == null -> if (isArabic) "كل الأبراج" else "All towers"
                                isDefault -> if (isArabic) "بدون برج" else "Unassigned"
                                else -> folder.name
                            }
                            var menuExpanded by remember(folder?.id) { mutableStateOf(false) }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 76.dp)
                                    .clickable {
                                        viewModel.selectFolder(folder?.id)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) CyanAccent.copy(alpha = 0.13f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                                border = BorderStroke(
                                    if (selected) 1.5.dp else 1.dp,
                                    if (selected) CyanAccent else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                folder == null -> Icons.Default.Dashboard
                                                isDefault -> Icons.Default.FolderOpen
                                                else -> Icons.Default.CellTower
                                            },
                                            contentDescription = null,
                                            tint = if (selected) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        if (selected) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = CyanAccent,
                                                modifier = Modifier.size(17.dp)
                                            )
                                        } else if (folder != null && !isDefault) {
                                            Box {
                                                IconButton(
                                                    onClick = { menuExpanded = true },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.MoreVert,
                                                        contentDescription = if (isArabic) "خيارات البرج" else "Tower options",
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                DropdownMenu(
                                                    expanded = menuExpanded,
                                                    onDismissRequest = { menuExpanded = false }
                                                ) {
                                                    DropdownMenuItem(
                                                        text = { Text(if (isArabic) "تعديل الاسم" else "Rename") },
                                                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                                                        onClick = {
                                                            menuExpanded = false
                                                            folderToEdit = folder
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(if (isArabic) "حذف البرج" else "Delete tower") },
                                                        leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = StatusRed) },
                                                        onClick = {
                                                            menuExpanded = false
                                                            folderToDelete = folder
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (folder == null) {
                                            if (isArabic) "${uiState.totalCount} جهاز" else "${uiState.totalCount} devices"
                                        } else {
                                            if (isArabic) "${folder.deviceCount} جهاز" else "${folder.deviceCount} devices"
                                        },
                                        fontSize = 11.sp,
                                        color = if (selected) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (rowItems.size == 1) Spacer(Modifier.weight(1f))
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

        // Filter Chips (Connection Status)
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedStatus == null,
                        onClick = { viewModel.onStatusSelected(null) },
                        label = { Text(if (isArabic) "كل الحالات" else "All Statuses") }
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.selectedStatus == DeviceStatus.ONLINE,
                        onClick = {
                            viewModel.onStatusSelected(
                                if (uiState.selectedStatus == DeviceStatus.ONLINE) null else DeviceStatus.ONLINE
                            )
                        },
                        label = { Text(if (isArabic) "متصل" else "Online") }
                    )
                }
                item {
                    FilterChip(
                        selected = uiState.selectedStatus == DeviceStatus.OFFLINE,
                        onClick = {
                            viewModel.onStatusSelected(
                                if (uiState.selectedStatus == DeviceStatus.OFFLINE) null else DeviceStatus.OFFLINE
                            )
                        },
                        label = { Text(if (isArabic) "غير متصل" else "Offline") }
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
                            text = if (isArabic) "لا توجد أجهزة مطابقة في هذا البرج."
                            else "No matching devices found in this tower.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isArabic) "يمكنك إضافة جهاز يدوياً بالضغط على '+ جهاز' أو إجراء فحص تلقائي عبر فاحص الشبكة."
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
            items(uiState.devices.sortedBy { if (it.deviceType == DeviceType.LINK) 0 else if (it.deviceType == DeviceType.SECTOR_AP) 1 else 2 }, key = { it.id }) { device ->
                val towerObj = uiState.folders.find { it.id == device.folderId }
                val towerName = if (device.folderId == DeviceFolderEntity.DEFAULT_FOLDER_ID || towerObj?.id == DeviceFolderEntity.DEFAULT_FOLDER_ID) {
                    if (isArabic) "أجهزة غير تابعة لبرج" else "Unassigned Devices"
                } else {
                    towerObj?.name ?: device.folderId
                }

                SavedDeviceCardItem(
                    device = device,
                    folderName = towerName,
                    isArabic = isArabic,
                    onEditClick = { deviceToEdit = device },
                    onDeleteClick = { deviceToDelete = device }
                )
            }
        }
    }

    // -------------------------------------------------------------
    // Add / Edit Device Dialog
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
        val globalApiPort = NetVisionApplication.instance.settingsRepository.getSettings().defaultMikroTikApiPort
        val apiCredentialStore = remember { com.netvision.app.security.DeviceCredentialStore(NetVisionApplication.instance) }
        val globalApiCredentials = remember { apiCredentialStore.getDefault() }
        val deviceApiCredentials = remember(existing?.id) { existing?.let { apiCredentialStore.get(it.id) } }
        val shownApiCredentials = deviceApiCredentials ?: globalApiCredentials
        var apiPortInput by remember(existing?.id) { mutableStateOf((existing?.apiPort ?: globalApiPort).toString()) }
        var apiUsernameInput by remember(existing?.id) { mutableStateOf(shownApiCredentials?.username.orEmpty()) }
        var apiPasswordInput by remember(existing?.id) { mutableStateOf(shownApiCredentials?.password.orEmpty()) }
        var editApiCredentials by remember(existing?.id) { mutableStateOf(false) }

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
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
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

                    // Field 3: Manufacturer
                    Text(if (isArabic) "الشركة المصنّعة" else "Manufacturer",
                        style = MaterialTheme.typography.titleMedium, fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(Manufacturer.MIKROTIK, Manufacturer.UBIQUITI, Manufacturer.MIMOSA, Manufacturer.OTHER)
                            .chunked(2).forEach { rowVendors ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    rowVendors.forEachIndexed { index, vendor ->
                                        val selected = selectedVendor == vendor
                                        val label = if (vendor == Manufacturer.OTHER && isArabic) "أخرى" else if (vendor == Manufacturer.OTHER) "Other" else vendor.displayName
                                        Surface(Modifier.weight(1f).height(54.dp).clickable { selectedVendor = vendor },
                                            shape = RoundedCornerShape(13.dp),
                                            color = selectionCardBackground(index, androidx.compose.foundation.isSystemInDarkTheme()),
                                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) selectionCardBorder(index) else MaterialTheme.colorScheme.outlineVariant)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(label, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                    color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF263445), textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                }
                            }
                    }

                    // Show the same MikroTik API settings block in BOTH add and edit flows.
                    val showMikroTikApiSettings = selectedVendor == Manufacturer.MIKROTIK &&
                        (showAddDeviceDialog || isEditing)
                    if (showMikroTikApiSettings) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            if (isArabic) "إعدادات MikroTik API" else "MikroTik API Connection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(if (isArabic) "البيانات الافتراضية المستخدمة لهذا الجهاز" else "Default credentials used for this device",
                                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    (if (isArabic) "المنفذ: " else "Port: ") + (existing?.apiPort ?: globalApiPort) +
                                        "   •   " + (if (isArabic) "المستخدم: " else "User: ") +
                                        (shownApiCredentials?.username ?: if (isArabic) "غير محدد" else "Not set") +
                                        "   •   " + (if (isArabic) "الرمز: " else "Password: ") +
                                        if (shownApiCredentials != null) "••••••••" else (if (isArabic) "غير محدد" else "Not set"),
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (isArabic) "تُستخدم بيانات الإعدادات العامة تلقائياً ما لم تخصص بيانات لهذا الجهاز." else "Global settings are used unless you customize this device.",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                if (!editApiCredentials) {
                                    apiPortInput = (existing?.apiPort ?: globalApiPort).toString()
                                    apiUsernameInput = shownApiCredentials?.username.orEmpty()
                                    apiPasswordInput = shownApiCredentials?.password.orEmpty()
                                }
                                editApiCredentials = !editApiCredentials
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (editApiCredentials) {
                                if (isArabic) "إخفاء التعديل واستخدام الافتراضي" else "Cancel customization"
                            } else {
                                if (isArabic) "تعديل بيانات API لهذا الجهاز" else "Customize API for this device"
                            })
                        }
                        if (editApiCredentials) {
                            OutlinedTextField(
                                value = apiPortInput,
                                onValueChange = { apiPortInput = it.filter { ch -> ch.isDigit() }.take(5) },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (isArabic) "منفذ API (TCP)" else "API Port (TCP)") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = apiUsernameInput,
                                onValueChange = { apiUsernameInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (isArabic) "اسم مستخدم API" else "API Username") },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = apiPasswordInput,
                                onValueChange = { apiPasswordInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (isArabic) "كلمة مرور API" else "API Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Text(
                                if (isArabic) "يمكنك تعديل المنفذ أو اسم المستخدم أو كلمة المرور لهذا الجهاز فقط. تُحفظ كلمة المرور مشفرة." else "Changes apply only to this device. Password is stored encrypted.",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Field 4: Device Type
                    Text(if (isArabic) "نوع الجهاز" else "Device type",
                        style = MaterialTheme.typography.titleMedium, fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(DeviceType.LINK, DeviceType.ROUTER, DeviceType.SECTOR_AP, DeviceType.SWITCH, DeviceType.CLIENT_DEVICE, DeviceType.OTHER)
                            .chunked(2).forEach { rowTypes ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    rowTypes.forEachIndexed { index, type ->
                                        val selected = selectedType == type
                                        Surface(Modifier.weight(1f).height(54.dp).clickable { selectedType = type },
                                            shape = RoundedCornerShape(13.dp),
                                            color = selectionCardBackground(index, androidx.compose.foundation.isSystemInDarkTheme()),
                                            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) selectionCardBorder(index) else MaterialTheme.colorScheme.outlineVariant)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(if (isArabic) type.labelAr else type.labelEn, fontSize = 15.sp,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                    color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF263445), textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                    if (rowTypes.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                    }

                    // Field 5: Assigned Tower
                    Text(if (isArabic) "البرج التابع له الجهاز" else "Assigned Tower", fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.folders.chunked(2).forEachIndexed { rowIndex, rowFolders ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowFolders.forEachIndexed { columnIndex, folder ->
                                    val index = rowIndex * 2 + columnIndex
                                    val selected = selectedFolder == folder.id
                                    val unassigned = folder.id == DeviceFolderEntity.DEFAULT_FOLDER_ID
                                    val label = if (unassigned) { if (isArabic) "أجهزة غير تابعة لبرج" else "Unassigned Devices" } else folder.name
                                    Surface(Modifier.weight(1f).height(56.dp).clickable { selectedFolder = folder.id },
                                        shape = RoundedCornerShape(13.dp),
                                        color = selectionCardBackground(index, androidx.compose.foundation.isSystemInDarkTheme()),
                                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) selectionCardBorder(index) else MaterialTheme.colorScheme.outlineVariant)) {
                                        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                            Icon(if (unassigned) Icons.Default.FolderOpen else Icons.Default.CellTower, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(label, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF263445), textAlign = TextAlign.Center, maxLines = 2)
                                            if (selected) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(15.dp)) }
                                        }
                                    }
                                }
                                if (rowFolders.size == 1) Spacer(Modifier.weight(1f))
                            }
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
                                folderId = selectedFolder,
                                apiPort = if (selectedVendor == Manufacturer.MIKROTIK) (if (editApiCredentials) apiPortInput.toIntOrNull()?.takeIf { it in 1..65535 } ?: globalApiPort else existing.apiPort ?: globalApiPort) else null,
                                apiUsername = if (selectedVendor == Manufacturer.MIKROTIK && editApiCredentials) apiUsernameInput else "",
                                apiPassword = if (selectedVendor == Manufacturer.MIKROTIK && editApiCredentials) apiPasswordInput else ""
                            )
                        } else {
                            viewModel.addDevice(
                                name = nameInput.ifBlank { "Device $ipInput" },
                                ipAddress = ipInput,
                                manufacturer = selectedVendor,
                                deviceType = selectedType,
                                folderId = selectedFolder,
                                apiPort = if (selectedVendor == Manufacturer.MIKROTIK) (if (editApiCredentials) apiPortInput.toIntOrNull()?.takeIf { it in 1..65535 } ?: globalApiPort else globalApiPort) else null,
                                apiUsername = if (selectedVendor == Manufacturer.MIKROTIK && editApiCredentials) apiUsernameInput else "",
                                apiPassword = if (selectedVendor == Manufacturer.MIKROTIK && editApiCredentials) apiPasswordInput else ""
                            )
                        }
                        showAddDeviceDialog = false
                        deviceToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = NavyDark)
                ) {
                    Text(if (isArabic) "حفظ" else "Save")
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
    // Create Tower Dialog (إضافة برج جديد)
    // -------------------------------------------------------------
    if (showCreateFolderDialog) {
        var folderNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(if (isArabic) "إضافة برج جديد" else "Create New Tower") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isArabic) "أدخل اسم البرج أو الموقع لتنظيم أجهزة الشبكة داخله (مثلاً: برج المركز، برج داقوق):"
                        else "Enter tower or site name to organize network devices (e.g. Tower Central, Tower North):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = folderNameInput,
                        onValueChange = { folderNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isArabic) "اسم البرج" else "Tower Name") },
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
                    Text(if (isArabic) "إنشاء البرج" else "Create")
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
    // Edit Tower Dialog (تعديل اسم البرج)
    // -------------------------------------------------------------
    if (folderToEdit != null) {
        val target = folderToEdit!!
        var editName by remember { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { folderToEdit = null },
            title = { Text(if (isArabic) "تعديل اسم البرج" else "Rename Tower") },
            text = {
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isArabic) "الاسم الجديد للبرج" else "New Tower Name") },
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
    // Delete Tower Dialog (حذف البرج مع نقل الأجهزة للأجهزة غير التابعة لبرج)
    // -------------------------------------------------------------
    if (folderToDelete != null) {
        val target = folderToDelete!!
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(if (isArabic) "حذف البرج" else "Delete Tower") },
            text = {
                Text(
                    text = if (isArabic) "هل أنت متأكد من حذف البرج '${target.name}'؟\n\nتنبيه أمان: لن يتم حذف أي جهاز نهائياً، وسيتم نقل جميع الأجهزة التابعة لهذا البرج (${target.deviceCount} جهاز) تلقائياً إلى 'أجهزة غير تابعة لبرج' لحمايتها."
                    else "Are you sure you want to delete tower '${target.name}'?\n\nSafety Note: No devices will be deleted. All ${target.deviceCount} devices in this tower will be safely moved to 'Unassigned Devices'."
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
        Manufacturer.MIMOSA -> Color(0xFF0D9488)
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

            Spacer(modifier = Modifier.height(6.dp))

            // Tower Indicator Badge
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CellTower,
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
