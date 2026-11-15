package com.netvision.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val titleEn: String,
    val titleAr: String,
    val icon: ImageVector
) {
    object Dashboard : Screen("dashboard", "Dashboard", "لوحة التحكم", Icons.Outlined.Dashboard)
    object Devices : Screen("devices", "Devices", "الأجهزة", Icons.Outlined.Devices)
    object Scanner : Screen("scanner", "Scanner", "الفاحص", Icons.Outlined.CenterFocusStrong)
    object Monitoring : Screen("monitoring", "Monitoring", "المراقبة", Icons.Outlined.MonitorHeart)
    object Ping : Screen("ping", "Ping", "Ping", Icons.Outlined.WifiTethering)
    object Settings : Screen("settings", "Settings", "الإعدادات", Icons.Outlined.Settings)
}

val bottomNavItems = listOf(
    Screen.Dashboard,
    Screen.Devices,
    Screen.Scanner,
    Screen.Monitoring,
    Screen.Ping
)
