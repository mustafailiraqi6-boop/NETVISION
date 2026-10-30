# NETVISION – Smart Network Discovery & Monitoring
## Native Android (Kotlin + Jetpack Compose + Material 3)

NETVISION is a professional network discovery and telemetry application designed for network engineers and IT administrators to discover, inventory, and monitor Ubiquiti (UBNT) and MikroTik equipment alongside routers, switches, and access points.

### Architecture & Technology Stack
- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose with Material 3 Design
- **Architecture**: MVVM (Model - View - ViewModel) with unidirectional data flow
- **Navigation**: Jetpack Navigation Compose
- **Local Persistence**: Room Database (SQLite ORM) with Kotlin Flow
- **Concurrency & Reactivity**: Kotlin Coroutines & StateFlow
- **Localization**: Built-in support for Arabic (العربية - RTL) and English (LTR)
- **Minimum Android SDK**: API 26 (Android 8.0 Oreo)
- **Target SDK**: API 35 (Android 15)

---

### Package Structure
```
app/src/main/java/com/netvision/app/
├── NetVisionApplication.kt          # Application class & DB initialization
├── MainActivity.kt                  # NavHost, Scaffold, bottom bar, RTL layout
├── models/
│   └── NetworkDevice.kt             # Domain models (NetworkDevice, Manufacturer, Status, Metrics)
├── database/
│   ├── DeviceEntity.kt              # Room entity & domain mapper
│   ├── DeviceDao.kt                 # Room DAO with reactive Flow queries
│   └── AppDatabase.kt               # Room database definition
├── repository/
│   ├── DeviceRepository.kt          # Single source of truth for device data
│   ├── ScannerRepository.kt         # Scanning and subnet repository
│   └── MonitoringRepository.kt      # Telemetry polling repository
├── network/
│   ├── NetworkDiscoveryService.kt   # Discovery service contract
│   ├── UbiquitiDeviceService.kt     # UBNT Discovery Protocol (UDP 10001)
│   └── MikroTikDeviceService.kt     # MNDP Protocol (UDP 5678) & RouterOS API
├── monitoring/
│   └── SnmpMonitoringService.kt     # SNMP v2c/v3 telemetry polling contract
├── settings/
│   └── AppSettings.kt               # App preferences (Theme, Language, CIDR subnets)
├── ui/
│   ├── theme/
│   │   └── Theme.kt                 # Material 3 Navy/Charcoal & Cyan/Electric Blue color schemes
│   ├── navigation/
│   │   └── NetVisionNavGraph.kt     # Bottom bar destinations & routes
│   ├── screens/
│   │   ├── DashboardScreen.kt       # KPIs, network health ring, vendor counts, activity log
│   │   ├── DevicesScreen.kt         # Searchable, filterable list with vendor badges
│   │   ├── ScannerScreen.kt         # Interactive scanning workflow with mock discovery disclaimer
│   │   ├── MonitoringScreen.kt      # SNMP metrics (Ping, Uptime, CPU, RAM, TX/RX, Signal dBm)
│   │   └── SettingsScreen.kt        # Language switch, Dark/Light mode, timeout, protocols
│   └── viewmodels/
│       ├── DashboardViewModel.kt    # Dashboard state & activity stream
│       ├── DevicesViewModel.kt      # Search, filter by vendor/status, sort
│       ├── ScannerViewModel.kt      # Scan execution state and progress
│       └── MonitoringViewModel.kt   # Live telemetry and device switcher
```

---

### How to Open & Build in Android Studio
1. Open **Android Studio Hedgehog / Iguana / Ladybug or newer**.
2. Select **File > Open** and choose this project root directory.
3. Allow Gradle to sync dependencies via `gradle/libs.versions.toml`.
4. Select target device / emulator (API 26+) and run `app` (`assembleDebug`).
