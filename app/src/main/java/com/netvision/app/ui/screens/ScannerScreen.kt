package com.netvision.app.ui.screens

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.DeviceType
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.NetworkRange
import com.netvision.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ScannerScreen(
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var scannedCount by remember { mutableStateOf(0) }
    var currentScannedIp by remember { mutableStateOf("192.168.1.1") }

    val presetRanges = remember {
        mutableStateListOf(
            NetworkRange("r1", "192.168.1.0/24", "LAN Main", true),
            NetworkRange("r2", "10.0.0.0/24", "Management VLAN", true),
            NetworkRange("r3", "172.16.10.0/24", "Guest WiFi", false)
        )
    }

    var customCidrInput by remember { mutableStateOf("192.168.1.0/24") }
    val discoveredScanResults = remember { mutableStateListOf<NetworkDevice>() }

    // Mock scanning loop
    fun runMockScan() {
        isScanning = true
        scanProgress = 0f
        scannedCount = 0
        discoveredScanResults.clear()

        coroutineScope.launch {
            val totalSteps = 100
            for (step in 1..totalSteps) {
                if (!isScanning) break
                delay(80)
                scanProgress = step / 100f
                scannedCount = (step * 2.54).toInt()
                currentScannedIp = "192.168.1.$scannedCount"

                if (step == 10) {
                    discoveredScanResults.add(
                        NetworkDevice(
                            id = "disc-1",
                            name = "UniFi Dream Machine Pro",
                            ipAddress = "192.168.1.1",
                            macAddress = "74:83:C2:11:44:88",
                            manufacturer = Manufacturer.UBIQUITI,
                            model = "UDM-Pro",
                            deviceType = DeviceType.GATEWAY,
                            status = DeviceStatus.ONLINE
                        )
                    )
                } else if (step == 25) {
                    discoveredScanResults.add(
                        NetworkDevice(
                            id = "disc-2",
                            name = "MikroTik CCR2004",
                            ipAddress = "192.168.1.2",
                            macAddress = "48:8F:5A:23:BC:10",
                            manufacturer = Manufacturer.MIKROTIK,
                            model = "CCR2004-16G-2S+",
                            deviceType = DeviceType.ROUTER,
                            status = DeviceStatus.ONLINE
                        )
                    )
                } else if (step == 50) {
                    discoveredScanResults.add(
                        NetworkDevice(
                            id = "disc-3",
                            name = "UniFi Switch Pro 24 PoE",
                            ipAddress = "192.168.1.10",
                            macAddress = "68:D7:9A:3C:99:E1",
                            manufacturer = Manufacturer.UBIQUITI,
                            model = "USW-Pro-24-PoE",
                            deviceType = DeviceType.SWITCH,
                            status = DeviceStatus.ONLINE
                        )
                    )
                } else if (step == 75) {
                    discoveredScanResults.add(
                        NetworkDevice(
                            id = "disc-4",
                            name = "UniFi U6 Enterprise AP",
                            ipAddress = "192.168.1.20",
                            macAddress = "AC:8B:A9:72:3F:4A",
                            manufacturer = Manufacturer.UBIQUITI,
                            model = "U6-Enterprise",
                            deviceType = DeviceType.ACCESS_POINT,
                            status = DeviceStatus.ONLINE
                        )
                    )
                }
            }
            isScanning = false
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Screen Header & Mock Disclaimer
        item {
            Column {
                Text(
                    text = if (isArabic) "فاحص الشبكة التلقائي" else "Network Scanner",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isArabic) "اكتشاف بروتوكولات UBNT Discovery (10001) و MikroTik MNDP (5678)" else "Discovery via UBNT UDP 10001, MikroTik MNDP 5678, & ARP sweeps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Prototype notice as required
                Surface(
                    color = ElectricBlueContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Text(
                        text = if (isArabic) "⚠️ بيئة فحص تجريبية تفاعلية: البيانات المعروضة تمثيلية للاختبار والتصميم قبل ربط حزم الشبكة الحقيقية."
                        else "⚠️ Prototype Mock Scanner: Workflow running on simulated network data for architecture testing before low-level socket bindings.",
                        fontSize = 11.sp,
                        color = CyanAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Subnet CIDR Range Configuration
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (isArabic) "نطاقات الشبكة المستهدفة (CIDR)" else "Target Network Ranges (CIDR)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customCidrInput,
                        onValueChange = { customCidrInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (isArabic) "إدخال نطاق IP" else "Enter CIDR Subnet") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isArabic) "النطاقات المحفوظة:" else "Active Subnets:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presetRanges) { range ->
                            FilterChip(
                                selected = range.isEnabled,
                                onClick = {
                                    val idx = presetRanges.indexOf(range)
                                    presetRanges[idx] = range.copy(isEnabled = !range.isEnabled)
                                },
                                label = { Text("${range.label} (${range.cidr})") }
                            )
                        }
                    }
                }
            }
        }

        // Scan Action Controls & Progress
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
                        Column {
                            Text(
                                text = if (isScanning) (if (isArabic) "جاري الفحص..." else "Scanning in Progress...")
                                else (if (isArabic) "الفاحص جاهز" else "Scanner Ready"),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isScanning) "Probing: $currentScannedIp" else "Subnet: $customCidrInput",
                                fontSize = 12.sp,
                                color = CyanAccent
                            )
                        }

                        if (!isScanning) {
                            Button(
                                onClick = { runMockScan() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = NavyDark),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isArabic) "بدء الفحص" else "Start Scan")
                            }
                        } else {
                            Button(
                                onClick = { isScanning = false },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusRed, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isArabic) "إيقاف الفحص" else "Stop Scan")
                            }
                        }
                    }

                    if (isScanning || scanProgress > 0f) {
                        Spacer(modifier = Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { scanProgress },
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
                                text = "${(scanProgress * 100).toInt()}% completed",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isArabic) "${discoveredScanResults.size} أجهزة تم اكتشافها" else "${discoveredScanResults.size} Discovered",
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
                    text = if (isArabic) "الأجهزة المكتشفة (${discoveredScanResults.size})" else "Discovered Equipment (${discoveredScanResults.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Discovered Device Cards
        if (discoveredScanResults.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isArabic) "اضغط على 'بدء الفحص' لبدء محاكاة اكتشاف الأجهزة" else "Tap 'Start Scan' to trigger discovery simulation",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(discoveredScanResults) { dev ->
                DeviceCardItem(device = dev, isArabic = isArabic)
            }
        }
    }
}
