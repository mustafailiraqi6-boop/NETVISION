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
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.DeviceType
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.repository.DeviceRepository
import com.netvision.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val repository = remember { DeviceRepository() }
    val allDevices by repository.getAllDevices().collectAsState(initial = emptyList())

    var searchQuery by remember { mutableStateOf("") }
    var selectedManufacturer by remember { mutableStateOf<Manufacturer?>(null) }
    var selectedStatus by remember { mutableStateOf<DeviceStatus?>(null) }
    var sortBy by remember { mutableStateOf("name") } // "name", "ip", "latency"

    val filteredDevices = remember(allDevices, searchQuery, selectedManufacturer, selectedStatus, sortBy) {
        allDevices.filter { device ->
            val matchesQuery = searchQuery.isEmpty() ||
                    device.name.contains(searchQuery, ignoreCase = true) ||
                    device.ipAddress.contains(searchQuery) ||
                    device.macAddress.contains(searchQuery, ignoreCase = true) ||
                    device.model.contains(searchQuery, ignoreCase = true)
            val matchesVendor = selectedManufacturer == null || device.manufacturer == selectedManufacturer
            val matchesStatus = selectedStatus == null || device.status == selectedStatus
            matchesQuery && matchesVendor && matchesStatus
        }.sortedWith { a, b ->
            when (sortBy) {
                "ip" -> a.ipAddress.compareTo(b.ipAddress)
                "latency" -> a.metrics.pingLatencyMs.compareTo(b.metrics.pingLatencyMs)
                else -> a.name.compareTo(b.name, ignoreCase = true)
            }
        }
    }

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
                    text = if (isArabic) "أجهزة الشبكة" else "Network Devices",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isArabic) "إجمالي الأجهزة المعروضة: ${filteredDevices.size}" else "Discovered Devices (${filteredDevices.size})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Search Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
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
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
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
                        selected = selectedManufacturer == null,
                        onClick = { selectedManufacturer = null },
                        label = { Text(if (isArabic) "جميع المصنعين" else "All Vendors") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedManufacturer == Manufacturer.UBIQUITI,
                        onClick = {
                            selectedManufacturer = if (selectedManufacturer == Manufacturer.UBIQUITI) null else Manufacturer.UBIQUITI
                        },
                        label = { Text("Ubiquiti (UBNT)") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedManufacturer == Manufacturer.MIKROTIK,
                        onClick = {
                            selectedManufacturer = if (selectedManufacturer == Manufacturer.MIKROTIK) null else Manufacturer.MIKROTIK
                        },
                        label = { Text("MikroTik") }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedManufacturer == Manufacturer.CISCO,
                        onClick = {
                            selectedManufacturer = if (selectedManufacturer == Manufacturer.CISCO) null else Manufacturer.CISCO
                        },
                        label = { Text("Cisco") }
                    )
                }
            }
        }

        // Device Items List
        items(filteredDevices, key = { it.id }) { device ->
            DeviceCardItem(device = device, isArabic = isArabic)
        }
    }
}

@Composable
fun DeviceCardItem(
    device: NetworkDevice,
    isArabic: Boolean
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
            // Header Row: Name & Status
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
                Text(
                    text = if (isArabic) device.status.labelAr else device.status.labelEn,
                    fontSize = 11.sp,
                    color = statusColor
                )
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

                if (device.status != DeviceStatus.OFFLINE) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${device.metrics.pingLatencyMs} ms",
                            fontSize = 13.sp,
                            color = if (device.metrics.pingLatencyMs > 30) StatusAmber else CyanAccent
                        )
                        Text(
                            text = "CPU ${device.metrics.cpuUsagePercent}%",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
