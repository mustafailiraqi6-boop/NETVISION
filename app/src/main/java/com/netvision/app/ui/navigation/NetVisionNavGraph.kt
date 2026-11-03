package com.netvision.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val titleEn: String,
    val titleAr: String,
    val icon: ImageVector
) {
    object Dashboard : Screen("dashboard", "Dashboard", "لوحة التحكم", Icons.Default.Dashboard)
    object Devices : Screen("devices", "Devices", "الأجهزة", Icons.Default.Devices)
    object Scanner : Screen("scanner", "Scanner", "الفاحص", Icons.Default.Radar)
    object Monitoring : Screen("monitoring", "Monitoring", "المراقبة", Icons.Default.QueryStats)
    object Ping : Screen("ping", "Ping", "Ping", Icons.Default.WifiTethering)
    object Settings : Screen("settings", "Settings", "الإعدادات", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Devices,
    Screen.Scanner,
    Screen.Monitoring,
    Screen.Ping,
    Screen.Settings
)
