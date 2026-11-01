package com.netvision.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.netvision.app.backup.BackupManager
import kotlinx.coroutines.launch
import com.netvision.app.models.DeviceStatus
import com.netvision.app.settings.AppLanguage
import com.netvision.app.settings.AppThemeMode
import com.netvision.app.ui.navigation.Screen
import com.netvision.app.ui.navigation.bottomNavItems
import com.netvision.app.ui.screens.DashboardScreen
import com.netvision.app.ui.screens.DeviceSnmpDetailsScreen
import com.netvision.app.ui.screens.DevicesScreen
import com.netvision.app.ui.screens.MonitoringScreen
import com.netvision.app.ui.screens.ScannerScreen
import com.netvision.app.ui.screens.SettingsScreen
import com.netvision.app.ui.viewmodels.MonitoringViewModel

class MainActivity : ComponentActivity() {
    override fun onStop() {
        // Android calls onStop when the activity leaves the foreground,
        // including Back navigation and removing the app from Recents.
        val backupFolder = BackupManager.getBackupFolder(this)
        if (backupFolder != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                BackupManager.createBackup(applicationContext, backupFolder)
            }
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var language by remember { mutableStateOf(AppLanguage.ARABIC) }
            var themeMode by remember { mutableStateOf(AppThemeMode.LIGHT) }

            val isRtl = language == AppLanguage.ARABIC
            val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                NetVisionTheme(darkTheme = themeMode == AppThemeMode.DARK) {
                    NetVisionApp(
                        language = language,
                        onLanguageChange = { language = it },
                        themeMode = themeMode,
                        onThemeModeChange = { themeMode = it }
                    )
                }
            }
        }
    }
}

@Composable
fun NetVisionApp(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route
    val isArabic = language == AppLanguage.ARABIC
    val isDarkTheme = themeMode == AppThemeMode.DARK

    val navBackground = if (isDarkTheme) Color(0xFF21466D) else Color(0xFFFFFFFF)
    val inactiveContent = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF64748B)
    val selectedBackground = Color(0xFFEEE4FF)
    val selectedContent = Color(0xFF6542A6)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(navBackground)
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = currentRoute == screen.route
                    val title = if (isArabic) screen.titleAr else screen.titleEn

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (selected) selectedBackground else Color.Transparent)
                            .clickable {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Dashboard.route) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                            .padding(horizontal = 2.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = title,
                                tint = if (selected) selectedContent else inactiveContent
                            )
                            Text(
                                text = title,
                                color = if (selected) selectedContent else inactiveContent,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    isArabic = isArabic,
                    onNavigateToDevices = { status ->
                        if (status == null) {
                            navController.navigate(Screen.Devices.route)
                        } else {
                            navController.navigate("devices-filter/${status.name}")
                        }
                    }
                )
            }
            composable(Screen.Devices.route) {
                DevicesScreen(isArabic = isArabic)
            }
            composable("devices-filter/{status}") { backStackEntry ->
                val status = backStackEntry.arguments?.getString("status")
                    ?.let { runCatching { DeviceStatus.valueOf(it) }.getOrNull() }
                DevicesScreen(isArabic = isArabic, initialStatus = status)
            }
            composable(Screen.Scanner.route) {
                ScannerScreen(isArabic = isArabic)
            }
            composable(Screen.Monitoring.route) {
                MonitoringScreen(
                    isArabic = isArabic,
                    onViewDeviceDetails = { deviceId -> navController.navigate("device-details/$deviceId") }
                )
            }
            composable("device-details/{deviceId}") { backStackEntry ->
                val deviceId = backStackEntry.arguments?.getString("deviceId").orEmpty()
                val monitoringEntry = remember(navController) {
                    navController.getBackStackEntry(Screen.Monitoring.route)
                }
                val monitoringViewModel: MonitoringViewModel = viewModel(monitoringEntry)
                DeviceSnmpDetailsScreen(
                    deviceId = deviceId,
                    isArabic = isArabic,
                    onBack = { navController.popBackStack() },
                    viewModel = monitoringViewModel
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    language = language,
                    onLanguageChange = onLanguageChange,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }
        }
    }
}
