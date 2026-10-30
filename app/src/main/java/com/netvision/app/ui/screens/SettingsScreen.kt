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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netvision.app.settings.AppLanguage
import com.netvision.app.settings.AppThemeMode
import com.netvision.app.ui.theme.CyanAccent
import com.netvision.app.ui.theme.ElectricBlue

@Composable
fun SettingsScreen(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isArabic = language == AppLanguage.ARABIC

    var defaultSubnet by remember { mutableStateOf("192.168.1.0/24") }
    var scanTimeoutMs by remember { mutableStateOf("1200") }
    var refreshIntervalSec by remember { mutableStateOf("5") }

    var ubntProtocolEnabled by remember { mutableStateOf(true) }
    var mikrotikMndpEnabled by remember { mutableStateOf(true) }
    var snmpPollingEnabled by remember { mutableStateOf(true) }

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
                    text = if (isArabic) "إعدادات التطبيق" else "Settings & Preferences",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isArabic) "اللغة، المظهر، بروتوكولات الفحص، ومؤقتات المراقبة" else "Language, display theme, discovery protocols, and timers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section 1: Localization & Display
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

        // Section 2: Network Scan Preferences
        item {
            SettingsGroupCard(
                title = if (isArabic) "تفضيلات فحص الشبكة" else "Scan Preferences",
                icon = Icons.Default.Tune
            ) {
                OutlinedTextField(
                    value = defaultSubnet,
                    onValueChange = { defaultSubnet = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isArabic) "الشبكة الفرعية الافتراضية" else "Default Subnet (CIDR)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = scanTimeoutMs,
                    onValueChange = { scanTimeoutMs = it },
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
                    onCheckedChange = { ubntProtocolEnabled = it }
                )
                ProtocolToggleRow(
                    label = "MikroTik MNDP Protocol (UDP 5678)",
                    checked = mikrotikMndpEnabled,
                    onCheckedChange = { mikrotikMndpEnabled = it }
                )
                ProtocolToggleRow(
                    label = "SNMP Auto-Detection (UDP 161)",
                    checked = snmpPollingEnabled,
                    onCheckedChange = { snmpPollingEnabled = it }
                )
            }
        }

        // Section 3: Monitoring Refresh Interval
        item {
            SettingsGroupCard(
                title = if (isArabic) "معدل تحديث المراقبة" else "Monitoring Refresh Interval",
                icon = Icons.Default.Autorenew
            ) {
                Text(
                    text = if (isArabic) "الفترة الزمنية بين استعلامات SNMP الحية:" else "Time interval between periodic SNMP counter polls:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("5s", "15s", "30s", "60s").forEach { interval ->
                        val isSelected = refreshIntervalSec == interval.replace("s", "")
                        FilterChip(
                            selected = isSelected,
                            onClick = { refreshIntervalSec = interval.replace("s", "") },
                            label = { Text(interval) }
                        )
                    }
                }
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
                    text = "Version 1.0.0-PROTOTYPE (API 26+ Android Native)",
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Architecture: Kotlin • Jetpack Compose • Material 3 • Room DB • Coroutines & Flow • MVVM",
                    fontSize = 11.sp,
                    color = ElectricBlue
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
