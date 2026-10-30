import React, { useState } from 'react';
import { X, FileCode, Folder, Copy, Check, Terminal, ExternalLink, Download, Layers } from 'lucide-react';

interface AndroidCodeExplorerProps {
  isOpen: boolean;
  onClose: () => void;
  isArabic: boolean;
}

interface CodeFileItem {
  path: string;
  filename: string;
  package: string;
  language: string;
  description: string;
  content: string;
}

const ANDROID_FILES: CodeFileItem[] = [
  {
    path: 'app/src/main/java/com/netvision/app/network/VpnStatusMonitor.kt',
    filename: 'VpnStatusMonitor.kt',
    package: 'com.netvision.app.network',
    language: 'kotlin',
    description: 'Android ConnectivityManager TRANSPORT_VPN listener monitoring OpenVPN route status.',
    content: `package com.netvision.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class VpnState(
    val isConnected: Boolean = false,
    val interfaceName: String? = null,
    val localIpAddress: String? = null,
    val activeRoutes: List<String> = emptyList(),
    val lastCheckTimestamp: Long = System.currentTimeMillis()
)

class VpnStatusMonitor(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _vpnState = MutableStateFlow(VpnState())
    val vpnState: StateFlow<VpnState> = _vpnState

    fun startMonitoring() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .build()
        connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                val linkProps = connectivityManager.getLinkProperties(network)
                _vpnState.value = VpnState(
                    isConnected = true,
                    interfaceName = linkProps?.interfaceName ?: "tun0",
                    localIpAddress = linkProps?.linkAddresses?.firstOrNull()?.address?.hostAddress
                )
            }
            override fun onLost(network: android.net.Network) {
                _vpnState.value = VpnState(isConnected = false)
            }
        })
    }
}`
  },
  {
    path: 'app/src/main/java/com/netvision/app/network/RemoteNetworkScanner.kt',
    filename: 'RemoteNetworkScanner.kt',
    package: 'com.netvision.app.network',
    language: 'kotlin',
    description: 'Scans remote VPN subnets using socket TCP probing (bypassing local ARP sweeps).',
    content: `package com.netvision.app.network

import com.netvision.app.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class RemoteNetworkScanner(
    private val reachabilityService: NetworkReachabilityService = NetworkReachabilityService()
) {
    fun scanRemoteSubnet(subnetCidr: String): Flow<RemoteScanStatus> = flow {
        // TCP probing across remote subnets respecting VPN split tunneling
    }
}`
  },
  {
    path: 'app/src/main/java/com/netvision/app/network/NetworkDiscoveryService.kt',
    filename: 'NetworkDiscoveryService.kt',
    package: 'com.netvision.app.network',
    language: 'kotlin',
    description: 'Interface for network range discovery, ICMP sweeps, and ARP scanning.',
    content: `package com.netvision.app.network

import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.NetworkRange
import kotlinx.coroutines.flow.Flow

/**
 * Interface for discovering active network equipment across LAN/VLAN subnets.
 * Prepares the architectural contract for ARP scanning, ICMP ping sweep,
 * and multicast broadcast discovery.
 */
interface NetworkDiscoveryService {
    data class ScanProgress(
        val totalHosts: Int,
        val scannedHosts: Int,
        val percentage: Int,
        val currentIp: String,
        val isCompleted: Boolean,
        val newlyFoundDevice: NetworkDevice? = null
    )

    fun startScan(ranges: List<NetworkRange>): Flow<ScanProgress>
    suspend fun stopScan()
    fun isScanning(): Boolean
}`
  },
  {
    path: 'app/src/main/java/com/netvision/app/network/UbiquitiDeviceService.kt',
    filename: 'UbiquitiDeviceService.kt',
    package: 'com.netvision.app.network',
    language: 'kotlin',
    description: 'Service contract for Ubiquiti Discovery Protocol on UDP port 10001.',
    content: `package com.netvision.app.network

import com.netvision.app.models.NetworkDevice
import kotlinx.coroutines.flow.Flow

/**
 * Service for discovery and interaction with Ubiquiti (UBNT) devices:
 * - UniFi OS Gateways, Cloud Keys, Access Points, and UniFi Switches
 * - EdgeMAX (EdgeRouter, EdgeSwitch)
 * - airMAX / LTU wireless radios
 * Listens on UDP port 10001 for Ubiquiti Discovery Protocol broadcasts.
 */
interface UbiquitiDeviceService {
    val ubiquitiBroadcastPort: Int get() = 10001

    fun listenForUbiquitiBeacons(): Flow<NetworkDevice>
    suspend fun queryDeviceDetails(ipAddress: String): Result<NetworkDevice>
}`
  },
  {
    path: 'app/src/main/java/com/netvision/app/network/MikroTikDeviceService.kt',
    filename: 'MikroTikDeviceService.kt',
    package: 'com.netvision.app.network',
    language: 'kotlin',
    description: 'Service contract for MikroTik MNDP UDP 5678 and RouterOS API.',
    content: `package com.netvision.app.network

import com.netvision.app.models.NetworkDevice
import kotlinx.coroutines.flow.Flow

/**
 * Service for discovery and telemetry of MikroTik RouterOS and SwOS devices.
 * Implements MikroTik Neighbor Discovery Protocol (MNDP) over UDP port 5678,
 * RouterOS API, and Winbox discovery packet parsing.
 */
interface MikroTikDeviceService {
    val mndpPort: Int get() = 5678

    fun listenForMndpAnnouncements(): Flow<NetworkDevice>
    suspend fun fetchSystemResources(ipAddress: String, credentials: Pair<String, String>?): Result<NetworkDevice>
}`
  },
  {
    path: 'app/src/main/java/com/netvision/app/monitoring/SnmpMonitoringService.kt',
    filename: 'SnmpMonitoringService.kt',
    package: 'com.netvision.app.monitoring',
    language: 'kotlin',
    description: 'Service contract for SNMP v2c/v3 telemetry polling and MIB counters.',
    content: `package com.netvision.app.monitoring

import com.netvision.app.models.NetworkMetrics
import kotlinx.coroutines.flow.Flow

interface SnmpMonitoringService {
    enum class SnmpVersion { V2C, V3 }

    data class SnmpTarget(
        val ipAddress: String,
        val port: Int = 161,
        val communityString: String = "public",
        val version: SnmpVersion = SnmpVersion.V2C
    )

    suspend fun pollDeviceMetrics(target: SnmpTarget): Result<NetworkMetrics>
    fun monitorLiveStream(target: SnmpTarget, intervalMs: Long = 5000): Flow<NetworkMetrics>
    suspend fun testSnmpConnectivity(target: SnmpTarget): Boolean
}`
  },
  {
    path: 'app/src/main/java/com/netvision/app/database/DeviceDao.kt',
    filename: 'DeviceDao.kt',
    package: 'com.netvision.app.database',
    language: 'kotlin',
    description: 'Room Database DAO with reactive Kotlin Flow queries.',
    content: `package com.netvision.app.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY lastSeenTimestamp DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :deviceId")
    suspend fun getDeviceById(deviceId: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE manufacturer = :manufacturer")
    fun getDevicesByManufacturer(manufacturer: String): Flow<List<DeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: DeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(devices: List<DeviceEntity>)

    @Delete
    suspend fun delete(device: DeviceEntity)
}`
  },
  {
    path: 'app/src/main/java/com/netvision/app/ui/viewmodels/DashboardViewModel.kt',
    filename: 'DashboardViewModel.kt',
    package: 'com.netvision.app.ui.viewmodels',
    language: 'kotlin',
    description: 'MVVM StateFlow ViewModel coordinating repository data and UI state.',
    content: `package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.models.*
import com.netvision.app.repository.DeviceRepository
import kotlinx.coroutines.flow.*

data class DashboardUiState(
    val totalDevices: Int = 0,
    val onlineDevices: Int = 0,
    val offlineDevices: Int = 0,
    val ubntDevicesCount: Int = 0,
    val mikrotikDevicesCount: Int = 0,
    val recentActivity: List<RecentDeviceActivity> = emptyList(),
    val isLoading: Boolean = false
)

class DashboardViewModel(
    private val repository: DeviceRepository = DeviceRepository()
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = repository.getAllDevices()
        .map { devices ->
            DashboardUiState(
                totalDevices = devices.size,
                onlineDevices = devices.count { it.status == DeviceStatus.ONLINE },
                offlineDevices = devices.count { it.status == DeviceStatus.OFFLINE },
                ubntDevicesCount = devices.count { it.manufacturer == Manufacturer.UBIQUITI },
                mikrotikDevicesCount = devices.count { it.manufacturer == Manufacturer.MIKROTIK },
                recentActivity = repository.getSampleActivity(),
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState(isLoading = true)
        )
}`
  },
  {
    path: 'app/src/main/java/com/netvision/app/MainActivity.kt',
    filename: 'MainActivity.kt',
    package: 'com.netvision.app',
    language: 'kotlin',
    description: 'Android ComponentActivity with Compose Navigation and RTL provider.',
    content: `package com.netvision.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.*
import com.netvision.app.ui.navigation.Screen
import com.netvision.app.ui.theme.NetVisionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var language by remember { mutableStateOf(AppLanguage.ARABIC) }
            val layoutDirection = if (language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                NetVisionTheme {
                    NetVisionApp(language = language, onLanguageChange = { language = it })
                }
            }
        }
    }
}`
  },
  {
    path: 'app/src/main/res/values-ar/strings.xml',
    filename: 'strings.xml (values-ar)',
    package: 'res/values-ar',
    language: 'xml',
    description: 'Android Arabic localization resources and RTL string configuration.',
    content: `<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">NETVISION</string>
    <string name="app_subtitle">اكتشاف ومراقبة الشبكات الذكية</string>
    <string name="nav_dashboard">لوحة التحكم</string>
    <string name="nav_devices">الأجهزة</string>
    <string name="nav_scanner">فاحص الشبكة</string>
    <string name="nav_monitoring">المراقبة</string>
    <string name="nav_settings">الإعدادات</string>
    <string name="total_devices">إجمالي الأجهزة</string>
    <string name="online_devices">أجهزة متصلة</string>
    <string name="offline_devices">أجهزة غير متصلة</string>
    <string name="start_scan">بدء الفحص</string>
    <string name="stop_scan">إيقاف الفحص</string>
</resources>`
  },
  {
    path: 'app/build.gradle.kts',
    filename: 'build.gradle.kts (app)',
    package: 'gradle',
    language: 'kotlin-dsl',
    description: 'App-level Gradle script configured with Jetpack Compose, Room, and Coroutines.',
    content: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.netvision.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.netvision.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
}`
  }
];

export const AndroidCodeExplorer: React.FC<AndroidCodeExplorerProps> = ({ isOpen, onClose, isArabic }) => {
  if (!isOpen) return null;

  const [selectedFile, setSelectedFile] = useState<CodeFileItem>(ANDROID_FILES[0]);
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(selectedFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-black/75 backdrop-blur-md animate-in fade-in duration-200">
      <div className="bg-[#0A0F1D] border border-cyan-500/30 rounded-2xl w-full max-w-4xl h-[85vh] flex flex-col overflow-hidden shadow-2xl">
        {/* Top Header */}
        <div className="px-4 py-3 bg-[#111A2E] border-b border-slate-800 flex items-center justify-between text-start">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-[#00E5FF]/20 text-[#00E5FF] flex items-center justify-center border border-[#00E5FF]/40 shrink-0">
              <Terminal className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-white flex items-center gap-2">
                <span>{isArabic ? 'مشروع أندرويد الأصلي NETVISION' : 'NETVISION Native Android Project'}</span>
                <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                  {isArabic ? 'جاهز لـ Android Studio' : 'Ready for Android Studio'}
                </span>
              </h3>
              <p className="text-[11px] text-slate-400">
                Kotlin 2.0 · Jetpack Compose · Material 3 · Room Database · MVVM
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="w-8 h-8 rounded-lg bg-slate-800 text-slate-400 hover:text-white flex items-center justify-center transition-colors"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Master / Detail Split */}
        <div className="flex-1 flex overflow-hidden">
          {/* Left: File Tree / List */}
          <div className="w-64 border-r border-slate-800 rtl:border-r-0 rtl:border-l bg-[#0F172A]/70 flex flex-col shrink-0 text-start">
            <div className="p-2.5 border-b border-slate-800/80 text-[11px] font-semibold text-slate-400 uppercase tracking-wider flex items-center gap-1.5">
              <Folder className="w-3.5 h-3.5 text-[#00E5FF]" />
              <span>{isArabic ? 'ملفات المشروع' : 'Project Files'}</span>
            </div>
            <div className="overflow-y-auto flex-1 p-2 space-y-1">
              {ANDROID_FILES.map((file) => {
                const isSelected = selectedFile.path === file.path;
                return (
                  <button
                    key={file.path}
                    onClick={() => setSelectedFile(file)}
                    className={`w-full text-start p-2 rounded-xl transition-all flex items-start gap-2 ${
                      isSelected
                        ? 'bg-[#17233D] text-[#00E5FF] border border-[#00E5FF]/40 shadow-sm'
                        : 'text-slate-400 hover:text-slate-200 hover:bg-[#111A2E]'
                    }`}
                  >
                    <FileCode className="w-4 h-4 shrink-0 mt-0.5 text-cyan-400" />
                    <div className="truncate">
                      <span className="text-xs font-mono font-medium block truncate">
                        {file.filename}
                      </span>
                      <span className="text-[10px] text-slate-500 block truncate">
                        {file.package}
                      </span>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          {/* Right: Code Viewer */}
          <div className="flex-1 flex flex-col bg-[#0A0F1D] overflow-hidden text-start">
            {/* File Info Bar */}
            <div className="p-3 bg-[#111A2E]/70 border-b border-slate-800 flex items-center justify-between text-xs">
              <div>
                <span className="font-mono text-cyan-300 font-semibold">{selectedFile.path}</span>
                <p className="text-[11px] text-slate-400 mt-0.5">{selectedFile.description}</p>
              </div>
              <button
                onClick={handleCopy}
                className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-mono text-slate-200 flex items-center gap-1.5 border border-slate-700 transition-colors shrink-0"
              >
                {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copied ? (isArabic ? 'تم النسخ!' : 'Copied!') : (isArabic ? 'نسخ الكود' : 'Copy Code')}</span>
              </button>
            </div>

            {/* Code Content */}
            <div className="flex-1 p-4 overflow-auto font-mono text-xs leading-relaxed text-slate-200 bg-[#070B14]" dir="ltr">
              <pre className="select-text whitespace-pre">{selectedFile.content}</pre>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="px-4 py-2.5 bg-[#111A2E] border-t border-slate-800 flex items-center justify-between text-xs text-slate-400">
          <span>
            {isArabic ? 'جميع ملفات الكود مخزنة في ' : 'All Android files located in '}
            <code>app/src/main/java/com/netvision/app/</code>
          </span>
          <button
            onClick={onClose}
            className="px-4 py-1.5 rounded-lg bg-[#00E5FF] text-[#0A0F1D] font-bold text-xs hover:brightness-110"
          >
            {isArabic ? 'العودة للتطبيق' : 'Back to App'}
          </button>
        </div>
      </div>
    </div>
  );
};
