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

    var communityInput by remember(appSettings.snmpCommunity) { mutableStateOf(appSettings.snmpCommunity) }
    var snmpPortInput by remember(appSettings.snmpPort) { mutableStateOf(appSettings.snmpPort.toString()) }
    var isCommunityVisible by remember { mutableStateOf(false) }

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

                // SNMP Community String Input with Password Mask
                OutlinedTextField(
                    value = communityInput,
                    onValueChange = {
                        communityInput = it
                        viewModel.updateSnmpCommunity(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isArabic) "اسم مجتمع SNMP (Community)" else "SNMP Community String") },
                    singleLine = true,
                    visualTransformation = if (isCommunityVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isCommunityVisible = !isCommunityVisible }) {
                            Icon(
                                imageVector = if (isCommunityVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Visibility",
                                tint = CyanAccent
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp)
                )

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

        // Section 4: About NETVISION
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
