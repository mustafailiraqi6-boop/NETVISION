package com.netvision.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.netvision.app.backup.BackupManager
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netvision.app.settings.AppLanguage
import com.netvision.app.settings.AppThemeMode
import com.netvision.app.ui.theme.CyanAccent
import com.netvision.app.ui.theme.ElectricBlue
import com.netvision.app.ui.viewmodels.SettingsViewModel

@Composable
fun SettingsScreen(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val isArabic = language == AppLanguage.ARABIC
    val appSettings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backupFolder by remember { mutableStateOf(BackupManager.getBackupFolder(context)) }
    var backupInProgress by remember { mutableStateOf(false) }
    var backupItems by remember { mutableStateOf<List<BackupManager.BackupInfo>>(emptyList()) }
    var restoreInProgress by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    LaunchedEffect(backupFolder) {
        backupItems = backupFolder?.let { BackupManager.listBackups(context, it) }.orEmpty()
    }

    val restorePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            BackupManager.setBackupFolder(context, uri)
            backupFolder = uri
            scope.launch {
                backupItems = BackupManager.listBackups(context, uri)
            }
            Toast.makeText(context, if (isArabic) "تم تحديد مجلد النسخ الاحتياطي" else "Backup folder selected", Toast.LENGTH_SHORT).show()
        }
    }

    fun runBackup() {
        val folder = backupFolder
        if (folder == null) {
            Toast.makeText(context, if (isArabic) "حدد مجلد النسخ الاحتياطي أولاً" else "Choose a backup folder first", Toast.LENGTH_SHORT).show()
            return
        }
        if (backupInProgress) return
        backupInProgress = true
        scope.launch {
            val result = BackupManager.createBackup(context, folder)
            backupInProgress = false
            result.onSuccess { name ->
                backupItems = BackupManager.listBackups(context, folder)
                Toast.makeText(context, (if (isArabic) "تم إنشاء النسخة: " else "Backup created: ") + name, Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(context, (if (isArabic) "فشل النسخ الاحتياطي: " else "Backup failed: ") + (error.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(if (isArabic) "تأكيد استعادة النسخة" else "Confirm backup restore") },
            text = {
                Text(
                    if (isArabic)
                        "سيتم استبدال قاعدة بيانات الأجهزة الحالية بالبيانات الموجودة في النسخة الاحتياطية. يُفضّل إنشاء نسخة حالية قبل المتابعة. هل تريد الاستمرار؟"
                    else
                        "The current device database will be replaced by the selected backup. It is recommended to create a backup first. Continue?"
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !restoreInProgress,
                    onClick = {
                        pendingRestoreUri = null
                        restoreInProgress = true
                        scope.launch {
                            val result = BackupManager.restoreBackup(context, uri)
                            restoreInProgress = false
                            result.onSuccess {
                                Toast.makeText(
                                    context,
                                    if (isArabic) "تمت الاستعادة. أعد تشغيل التطبيق لتحديث جميع البيانات." else "Restore complete. Restart the app to reload all data.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }.onFailure { error ->
                                Toast.makeText(
                                    context,
                                    (if (isArabic) "فشلت الاستعادة: " else "Restore failed: ") + (error.message ?: ""),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) { Text(if (isArabic) "استعادة" else "Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }

    var communityInput by remember { mutableStateOf("") }
    var isCommunityVisible by remember { mutableStateOf(false) }
    val communityList = appSettings.snmpCommunities
    fun addCommunity() {
        val value = communityInput.trim()
        if (value.isNotEmpty() && value !in communityList) {
            viewModel.updateSnmpCommunities(communityList + value)
            communityInput = ""
        }
    }
    var snmpPortInput by remember(appSettings.snmpPort) { mutableStateOf(appSettings.snmpPort.toString()) }
    var mikrotikApiPortInput by remember(appSettings.defaultMikroTikApiPort) { mutableStateOf(appSettings.defaultMikroTikApiPort.toString()) }
    var defaultSubnet by remember(appSettings.defaultSubnet) { mutableStateOf(appSettings.defaultSubnet) }
    var scanTimeoutMs by remember(appSettings.scanTimeoutMs) { mutableStateOf(appSettings.scanTimeoutMs.toString()) }

    var ubntProtocolEnabled by remember(appSettings.enableUbiquitiDiscovery) { mutableStateOf(appSettings.enableUbiquitiDiscovery) }
    var mikrotikMndpEnabled by remember(appSettings.enableMikroTikMndp) { mutableStateOf(appSettings.enableMikroTikMndp) }
    var snmpPollingEnabled by remember(appSettings.enableSnmpPolling) { mutableStateOf(appSettings.enableSnmpPolling) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Screen Title
        item {
            Column {
                Text(
                    text = if (isArabic) "إعدادات التطبيق و SNMP" else "Settings & SNMP Setup",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isArabic) "ضبط مجتمع SNMP، بروتوكولات الفحص، اللغة والمظهر" else "SNMP Community, version, scan timeouts, and display options",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section 1: SNMP Protocol Configuration
        item {
            SettingsGroupCard(
                title = if (isArabic) "إعدادات بروتوكول SNMP" else "SNMP Protocol Configuration",
                icon = Icons.Default.SettingsEthernet
            ) {
                Text(
                    text = if (isArabic) "تخصيص مجتمع SNMP والإصدار المستهدف للاتصال بالأجهزة:" else "Custom Community string and SNMP target version:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = communityInput,
                    onValueChange = { communityInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isArabic) "اسم مجتمع SNMP (Community)" else "SNMP Community String") },
                    singleLine = true,
                    visualTransformation = if (isCommunityVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isCommunityVisible = !isCommunityVisible }) {
                                Icon(if (isCommunityVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = CyanAccent)
                            }
                            IconButton(onClick = { addCommunity() }, enabled = communityInput.isNotBlank()) {
                                Icon(Icons.Default.Add, contentDescription = if (isArabic) "إضافة" else "Add", tint = CyanAccent)
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                )
                Text(
                    text = if (isArabic) "المجاميع المحفوظة (تُجرّب بالترتيب):" else "Saved communities (tried in order):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                communityList.forEachIndexed { index, community ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(text = "${index + 1}. " + if (isCommunityVisible) community else "••••••", modifier = Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        IconButton(onClick = { if (index > 0) viewModel.updateSnmpCommunities(communityList.toMutableList().also { val item = it.removeAt(index); it.add(index - 1, item) }) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up") }
                        IconButton(onClick = { if (index < communityList.lastIndex) viewModel.updateSnmpCommunities(communityList.toMutableList().also { val item = it.removeAt(index); it.add(index + 1, item) }) }, enabled = index < communityList.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down") }
                        IconButton(onClick = { if (communityList.size > 1) viewModel.updateSnmpCommunities(communityList - community) }) { Icon(Icons.Default.DeleteOutline, contentDescription = if (isArabic) "حذف" else "Remove", tint = MaterialTheme.colorScheme.error) }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                // SNMP Version Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isArabic) "إصدار SNMP (Version):" else "SNMP Version:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = appSettings.snmpVersion == "v2c" || appSettings.snmpVersion == "auto",
                            onClick = { viewModel.updateSnmpVersion("v2c") },
                            label = { Text("v2c (Auto)") }
                        )
                        FilterChip(
                            selected = appSettings.snmpVersion == "v1",
                            onClick = { viewModel.updateSnmpVersion("v1") },
                            label = { Text("v1") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SNMP UDP Port Input
                OutlinedTextField(
                    value = snmpPortInput,
                    onValueChange = {
                        snmpPortInput = it
                        viewModel.updateSnmpPort(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isArabic) "منفذ UDP لبروتوكول SNMP" else "SNMP UDP Port") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        // MikroTik RouterOS API default port
        item {
            SettingsGroupCard(
                title = if (isArabic) "إعدادات MikroTik API" else "MikroTik API Settings",
                icon = Icons.Default.SettingsEthernet
            ) {
                Text(
                    text = if (isArabic) "المنفذ الافتراضي لأجهزة MikroTik الجديدة. يمكن تخصيص منفذ مختلف لكل جهاز عند الإضافة أو التعديل." else "Default port for MikroTik devices. You can override it per device when adding or editing.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = mikrotikApiPortInput,
                    onValueChange = { value ->
                        mikrotikApiPortInput = value.filter { it.isDigit() }.take(5)
                        mikrotikApiPortInput.toIntOrNull()?.takeIf { it in 1..65535 }?.let {
                            viewModel.updateDefaultMikroTikApiPort(it.toString())
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isArabic) "منفذ API الافتراضي (TCP)" else "Default API Port (TCP)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )
                Text(
                    text = if (isArabic) "القيمة الحالية: ${appSettings.defaultMikroTikApiPort}" else "Current default: ${appSettings.defaultMikroTikApiPort}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Section 2: Localization & Display
        item {
            SettingsGroupCard(
                title = if (isArabic) "اللغة والمظهر" else "Language & Display",
                icon = Icons.Default.Language
            ) {
                // Language Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isArabic) "لغة الواجهة" else "Application Language",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isArabic) "العربية (RTL) أو English (LTR)" else "Arabic (RTL) or English (LTR)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = language == AppLanguage.ENGLISH,
                            onClick = { onLanguageChange(AppLanguage.ENGLISH) },
                            label = { Text("English") }
                        )
                        FilterChip(
                            selected = language == AppLanguage.ARABIC,
                            onClick = { onLanguageChange(AppLanguage.ARABIC) },
                            label = { Text("العربية") }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                // Theme Mode Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isArabic) "نمط المظهر" else "Theme Appearance",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (themeMode == AppThemeMode.DARK) "Dark Navy & Charcoal" else "Light Mode",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = themeMode == AppThemeMode.DARK,
                            onClick = { onThemeModeChange(AppThemeMode.DARK) },
                            label = { Text(if (isArabic) "داكن" else "Dark") }
                        )
                        FilterChip(
                            selected = themeMode == AppThemeMode.LIGHT,
                            onClick = { onThemeModeChange(AppThemeMode.LIGHT) },
                            label = { Text(if (isArabic) "فاتح" else "Light") }
                        )
                    }
                }
            }
        }

        // Section 3: Network Scan Preferences
        item {
            SettingsGroupCard(
                title = if (isArabic) "تفضيلات فحص الشبكة" else "Scan Preferences",
                icon = Icons.Default.Tune
            ) {
                OutlinedTextField(
                    value = defaultSubnet,
                    onValueChange = {
                        defaultSubnet = it
                        viewModel.updateSettings(appSettings.copy(defaultSubnet = it))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isArabic) "الشبكة الفرعية الافتراضية" else "Default Subnet (CIDR)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = scanTimeoutMs,
                    onValueChange = {
                        scanTimeoutMs = it
                        val timeoutInt = it.toIntOrNull() ?: 1200
                        viewModel.updateSettings(appSettings.copy(scanTimeoutMs = timeoutInt))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isArabic) "مهلة استجابة المقبس (ms)" else "Probe Timeout (ms)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = if (isArabic) "بروتوكولات الاكتشاف التلقائي:" else "Discovery Protocols Enabled:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ProtocolToggleRow(
                    label = "Ubiquiti Discovery Protocol (UDP 10001)",
                    checked = ubntProtocolEnabled,
                    onCheckedChange = {
                        ubntProtocolEnabled = it
                        viewModel.updateSettings(appSettings.copy(enableUbiquitiDiscovery = it))
                    }
                )
                ProtocolToggleRow(
                    label = "MikroTik MNDP Protocol (UDP 5678)",
                    checked = mikrotikMndpEnabled,
                    onCheckedChange = {
                        mikrotikMndpEnabled = it
                        viewModel.updateSettings(appSettings.copy(enableMikroTikMndp = it))
                    }
                )
                ProtocolToggleRow(
                    label = "SNMP Auto-Detection (UDP 161)",
                    checked = snmpPollingEnabled,
                    onCheckedChange = {
                        snmpPollingEnabled = it
                        viewModel.updateSettings(appSettings.copy(enableSnmpPolling = it))
                    }
                )
            }
        }

        // Section 4: Backup & Restore
        item {
            SettingsGroupCard(
                title = if (isArabic) "النسخ الاحتياطي" else "Backup",
                icon = Icons.Default.Backup
            ) {
                Text(
                    text = if (isArabic)
                        "احفظ بيانات الأجهزة والإعدادات في ملف ZIP. يحتفظ التطبيق بآخر 3 نسخ."
                    else
                        "Save your device database and settings to ZIP. The three newest backups are kept.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (backupFolder != null) {
                        if (isArabic) "مجلد النسخ الاحتياطي محدد" else "Backup folder selected"
                    } else {
                        if (isArabic) "لم يتم تحديد مجلد للحفظ" else "No backup folder selected"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { folderPicker.launch(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isArabic) "تحديد مكان حفظ النسخ" else "Choose backup location")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { runBackup() },
                    enabled = backupFolder != null && !backupInProgress,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (backupInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (backupInProgress) {
                            if (isArabic) "جارٍ إنشاء النسخة..." else "Creating backup..."
                        } else {
                            if (isArabic) "إنشاء نسخة احتياطية الآن" else "Create backup now"
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { restorePicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                    enabled = !restoreInProgress,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (restoreInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Restore, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (restoreInProgress) {
                            if (isArabic) "جارٍ استعادة النسخة..." else "Restoring backup..."
                        } else {
                            if (isArabic) "استعادة نسخة احتياطية" else "Restore backup"
                        }
                    )
                }
                Text(
                    text = if (isArabic)
                        "يتم الاحتفاظ بآخر 3 نسخ تلقائياً. الاستعادة تستبدل قاعدة البيانات الحالية."
                    else
                        "The three newest backups are retained automatically. Restore replaces the current database.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (backupItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (isArabic) "النسخ الاحتياطية المحفوظة" else "Saved backups",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    backupItems.forEachIndexed { index, backup ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(backup.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    if (backup.modifiedAt > 0L)
                                        java.text.SimpleDateFormat("yyyy-MM-dd  HH:mm", java.util.Locale.getDefault()).format(java.util.Date(backup.modifiedAt))
                                    else if (isArabic) "التاريخ غير متاح" else "Date unavailable",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text("${index + 1}/3", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        if (index < backupItems.lastIndex) Spacer(modifier = Modifier.height(6.dp))
                    }
                }

            }
        }

        // Section 5: About NETVISION
        item {
            SettingsGroupCard(
                title = if (isArabic) "حول تطبيق NETVISION" else "About NETVISION",
                icon = Icons.Default.Info
            ) {
                Text(
                    text = "NETVISION – Smart Network Discovery & Monitoring",
                    style = MaterialTheme.typography.titleMedium,
                    color = CyanAccent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Version 1.1.0 (SNMP v2c/v3 Community Support)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isArabic)
                        "تطبيق أندرويد احترافي مصمم لاكتشاف ومراقبة أجهزة Ubiquiti و MikroTik ومعدات الشبكات عبر بروتوكولات UBNT و MNDP و SNMP."
                    else
                        "Professional network discovery and telemetry application engineered for Ubiquiti (UBNT), MikroTik RouterOS, and enterprise network infrastructure.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun SettingsGroupCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun ProtocolToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyanAccent,
                checkedTrackColor = ElectricBlue.copy(alpha = 0.5f)
            )
        )
    }
}
