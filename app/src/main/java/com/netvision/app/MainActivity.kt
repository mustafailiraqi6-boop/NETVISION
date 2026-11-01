package com.netvision.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.netvision.app.ui.theme.CyanAccent
import com.netvision.app.ui.theme.ElectricBlue
import com.netvision.app.ui.theme.NavyBorder
import com.netvision.app.ui.theme.NavyCard
import com.netvision.app.ui.theme.NavySurface
import com.netvision.app.ui.theme.NetVisionTheme
import com.netvision.app.ui.theme.TextMutedDark
import com.netvision.app.ui.theme.TextPrimaryDark
import com.netvision.app.ui.viewmodels.MonitoringViewModel

class MainActivity : ComponentActivity() {
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                bottomNavItems.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = if (isArabic) screen.titleAr else screen.titleEn
                            )
                        },
                        label = {
                            Text(text = if (isArabic) screen.titleAr else screen.titleEn)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                            indicatorColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                            unselectedIconColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                            unselectedTextColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f)
                        )
                    )
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
                DashboardScreen(isArabic = isArabic, onNavigateToDevices = { navController.navigate(Screen.Devices.route) })
            }
            composable(Screen.Devices.route) {
                DevicesScreen(isArabic = isArabic)
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
