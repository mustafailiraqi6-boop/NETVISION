package com.netvision.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netvision.app.models.NetworkDevice
import com.netvision.app.ui.viewmodels.MonitoringViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

private data class PingTarget(val id: String, val name: String, val ip: String, val groupId: String, val groupName: String, val managed: Boolean)
private data class PingResult(val online: Boolean = false, val latency: String = "—")

private class ContinuousPingManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()\n    private val processes = mutableMapOf<String, Process>()
    private val _results = MutableStateFlow<Map<String, PingResult>>(emptyMap())
    val results = _results.asStateFlow()

    fun start(target: PingTarget) {
        if (jobs[target.id]?.isActive == true) return
        jobs[target.id] = scope.launch {
            try {
                val process = ProcessBuilder("ping", "-i", "1", "-W", "2", target.ip).redirectErrorStream(true).start()
                processes[target.id] = process\n                val reader = process.inputStream.bufferedReader()
                while (isActive) {
                    val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                    val time = Regex("time[=<]([0-9.]+)\\s*ms", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1)
                    val success = line.contains("bytes from", true) || time != null
                    if (success) {
                        _results.value = _results.value + (target.id to PingResult(true, time?.let { "$it ms" } ?: "<1 ms"))
                    } else if (line.contains("unreachable", true) || line.contains("timeout", true) || line.contains("100% packet loss", true)) {
                        _results.value = _results.value + (target.id to PingResult(false, "—"))
                    }
                }
                process.destroy()
            } catch (_: Exception) {
                _results.value = _results.value + (target.id to PingResult(false, "غير مدعوم"))
            }
        }
    }

    fun stop(id: String) {
        jobs.remove(id)?.cancel()
        _results.value = _results.value + (id to PingResult())
    }
    fun stopAll() { jobs.keys.toList().forEach(::stop) }
    fun isRunning(id: String) = jobs[id]?.isActive == true
}

private data class ManualPingDevice(val id: String, val name: String, val ip: String, val group: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingScreen(isArabic: Boolean, viewModel: MonitoringViewModel) {
    val context = LocalContext.current
    val devices by viewModel.savedDevices.collectAsState()
    val folders by viewModel.savedFolders.collectAsState()
    val manager = remember { ContinuousPingManager() }
    val results by manager.results.collectAsState()
    val prefs = remember { context.getSharedPreferences("netvision_ping", Context.MODE_PRIVATE) }
    var manual by remember { mutableStateOf(loadManual(prefs)) }
    var favorites by remember { mutableStateOf(prefs.getStringSet("favorite_groups", emptySet())?.toSet() ?: emptySet()) }
    var showAdd by remember { mutableStateOf(false) }\n    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    val targets = remember(devices, folders, manual) {
        val folderNames = folders.associate { it.id to it.name }
        devices.map { d -> PingTarget("monitor-${d.id}", d.name, d.ipAddress, d.folderId, folderNames[d.folderId] ?: "أجهزة غير تابعة لبرج", true) } +
            manual.map { PingTarget(it.id, it.name, it.ip, "custom:${it.group}", it.group, false) }
    }
    val grouped = targets.groupBy { it.groupId }.toList().sortedWith(compareByDescending<Pair<String, List<PingTarget>>> { it.first in favorites }.thenBy { it.second.firstOrNull()?.groupName ?: "" })

    DisposableEffect(Unit) { onDispose { manager.stopAll() } }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(if (editingId == null) { if (isArabic) "إضافة جهاز إلى Ping" else "Add Ping device" } else { if (isArabic) "تعديل الجهاز" else "Edit device" }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text(if (isArabic) "اسم الجهاز" else "Device name") }, singleLine = true)
                    OutlinedTextField(ip, { ip = it }, label = { Text("IP Address") }, singleLine = true)
                    OutlinedTextField(group, { group = it }, label = { Text(if (isArabic) "المجموعة / البرج" else "Group / Tower") }, singleLine = true)
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = {
                val cleanIp = ip.trim()
                val valid = cleanIp.split(".").size == 4 && cleanIp.split(".").all { it.toIntOrNull()?.let { value -> value in 0..255 } == true }
                when {
                    name.isBlank() || group.isBlank() || !valid -> error = if (isArabic) "تأكد من الاسم والمجموعة وعنوان IP" else "Enter a valid name, group and IPv4 address"
                    targets.any { it.id != editingId && it.ip.equals(cleanIp, true) } -> error = if (isArabic) "عنوان IP موجود مسبقاً" else "This IP already exists"
                    else -> {
                        manual = manual + ManualPingDevice(UUID.randomUUID().toString(), name.trim(), cleanIp, group.trim())
                        saveManual(prefs, manual)
                        name = ""; ip = ""; group = ""; error = ""; showAdd = false
                    }
                }
            }) { Text(if (editingId == null) { if (isArabic) "إضافة" else "Add" } else { if (isArabic) "حفظ" else "Save" }) } },
            dismissButton = { TextButton(onClick = { showAdd = false; editingId = null; error = "" }) { Text(if (isArabic) "إلغاء" else "Cancel") } }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(if (isArabic) "اختبار Ping المباشر" else "Live Ping", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(if (isArabic) "فحص مستمر للأجهزة وزمن الاستجابة" else "Continuous ICMP reachability and latency", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                FilledTonalButton(onClick = { editingId = null; name = ""; ip = ""; group = ""; showAdd = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text(if (isArabic) "إضافة" else "Add") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { targets.forEach(manager::start) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, null); Text(if (isArabic) "تشغيل الكل" else "Start all") }
                OutlinedButton(onClick = { manager.stopAll() }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Stop, null); Text(if (isArabic) "إيقاف الكل" else "Stop all") }
            }
        }
        if (targets.isEmpty()) item { Text(if (isArabic) "لا توجد أجهزة بعد. أضف جهازاً أو أضف أجهزة من قسم المراقبة." else "No devices yet. Add one or save devices in Monitoring.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(grouped, key = { it.first }) { (groupId, groupTargets) ->
            val groupName = groupTargets.first().groupName
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            favorites = if (groupId in favorites) favorites - groupId else favorites + groupId
                            prefs.edit().putStringSet("favorite_groups", favorites).apply()
                        }) { Icon(if (groupId in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, tint = if (groupId in favorites) Color(0xFFE6A23C) else MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(groupName, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${groupTargets.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { groupTargets.forEach(manager::start) }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayArrow, null); Text(if (isArabic) "تشغيل" else "Start") }
                        OutlinedButton(onClick = { groupTargets.forEach { manager.stop(it.id) } }, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Stop, null); Text(if (isArabic) "إيقاف" else "Stop") }
                    }
                    groupTargets.forEach { target ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            val result = results[target.id] ?: PingResult()
                            Box(Modifier.size(10.dp).background(if (result.online) Color(0xFF20B86A) else Color(0xFFE05252), CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(target.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(target.ip, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Text(result.latency, color = if (result.online) Color(0xFF20B86A) else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            if (!target.managed) {
                                TextButton(onClick = {
                                    manager.stop(target.id)
                                    manual = manual.filterNot { it.id == target.id }
                                    saveManual(prefs, manual)
                                }) { Text(if (isArabic) "حذف" else "Delete", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadManual(prefs: android.content.SharedPreferences): List<ManualPingDevice> =
    prefs.getString("manual_devices", "").orEmpty().lines().mapNotNull { line ->
        val p = line.split('|')
        if (p.size == 4) ManualPingDevice(p[0], p[1], p[2], p[3]) else null
    }

private fun saveManual(prefs: android.content.SharedPreferences, devices: List<ManualPingDevice>) {
    prefs.edit().putString("manual_devices", devices.joinToString("\n") { "${it.id}|${it.name.replace("|", "")}|${it.ip}|${it.group.replace("|", "")}" }).apply()
}
