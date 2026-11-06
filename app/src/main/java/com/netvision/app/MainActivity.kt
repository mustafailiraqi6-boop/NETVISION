package com.netvision.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import com.netvision.app.ui.screens.PingScreen
import com.netvision.app.ui.screens.ScannerScreen
import com.netvision.app.ui.screens.SettingsScreen
import com.netvision.app.ui.viewmodels.MonitoringViewModel
import com.netvision.app.ui.viewmodels.SettingsViewModel
import com.netvision.app.ui.theme.NetVisionTheme

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        NetVisionApplication.notifyAppForegrounded()
    }

    override fun onStop() {
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
            val settingsViewModel: SettingsViewModel = viewModel()
            val savedSettings by settingsViewModel.settings.collectAsState()
            val language = savedSettings.language
            val themeMode = savedSettings.themeMode
            val layoutDirection = if (language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                NetVisionTheme(darkTheme = themeMode == AppThemeMode.DARK) {
                    NetVisionApp(
                        language = language,
                        onLanguageChange = { selected -> settingsViewModel.updateSettings(savedSettings.copy(language = selected)) },
                        themeMode = themeMode,
                        onThemeModeChange = { selected -> settingsViewModel.updateSettings(savedSettings.copy(themeMode = selected)) }
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
    val monitoringViewModel: MonitoringViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route
    val isArabic = language == AppLanguage.ARABIC
    val isDarkTheme = themeMode == AppThemeMode.DARK

    val navBackground = if (isDarkTheme) Color(0xFF101012) else Color(0xFFF7F7F9)
    val inactiveContent = if (isDarkTheme) Color(0xFF9B9BA3) else Color(0xFF777982)
    val activeAccent = if (isDarkTheme) Color(0xFF37D39A) else Color(0xFF087F59)
    val selectedContent = if (isDarkTheme) Color(0xFFF7F7F8) else Color(0xFF25232A)
    val selectedIndex = bottomNavItems.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    val animatedIndicatorIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 260f),
        label = "bottomNavCurvePosition"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(40.dp))
                    .background(navBackground)
            ) {
                val itemWidth = maxWidth / bottomNavItems.size
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val centerX = (animatedIndicatorIndex + 0.5f) * (w / bottomNavItems.size)
                    val halfCurve = (w / bottomNavItems.size) * 0.48f
                    val rise = h * 0.66f
                    val baseY = h - 2.dp.toPx()
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, baseY)
                        lineTo(centerX - halfCurve, baseY)
                        cubicTo(
                            centerX - halfCurve * 0.35f, baseY,
                            centerX - halfCurve * 0.62f, rise,
                            centerX, rise
                        )
                        cubicTo(
                            centerX + halfCurve * 0.62f, rise,
                            centerX + halfCurve * 0.35f, baseY,
                            centerX + halfCurve, baseY
                        )
                        lineTo(w, baseY)
                    }
                    drawPath(
                        path = path,
                        color = activeAccent.copy(alpha = 0.22f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 7.dp.toPx())
                    )
                    drawPath(
                        path = path,
                        color = activeAccent,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 5.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        val title = if (isArabic) screen.titleAr else screen.titleEn
                        val iconLift by animateDpAsState(
                            targetValue = if (selected) 5.dp else 0.dp,
                            animationSpec = spring(dampingRatio = 0.72f, stiffness = 300f),
                            label = "navIconLift"
                        )
                        Box(
                            modifier = Modifier.weight(1f).fillMaxSize().clickable {
                                if (currentRoute != screen.route) navController.navigate(screen.route) {
                                    popUpTo(Screen.Dashboard.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    screen.icon,
                                    contentDescription = title,
                                    tint = if (selected) selectedContent else inactiveContent,
                                    modifier = Modifier.graphicsLayer {
                                        translationY = -iconLift.toPx()
                                    }
                                )
                                Text(
                                    title,
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
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = Screen.Dashboard.route, modifier = Modifier.padding(innerPadding)) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(isArabic = isArabic, monitoringViewModel = monitoringViewModel, onNavigateToDevices = { status ->
                    if (status == null) navController.navigate(Screen.Devices.route) else navController.navigate("devices-filter/${status.name}")
                })
            }
            composable(Screen.Devices.route) { DevicesScreen(isArabic = isArabic) }
            composable("devices-filter/{status}") { backStackEntry ->
                val status = backStackEntry.arguments?.getString("status")?.let { runCatching { DeviceStatus.valueOf(it) }.getOrNull() }
                DevicesScreen(isArabic = isArabic, initialStatus = status)
            }
            composable(Screen.Scanner.route) { ScannerScreen(isArabic = isArabic) }
            composable(Screen.Monitoring.route) {
                MonitoringScreen(isArabic = isArabic, onViewDeviceDetails = { deviceId -> navController.navigate("device-details/$deviceId") }, viewModel = monitoringViewModel)
            }
            composable(Screen.Ping.route) { PingScreen(isArabic = isArabic, viewModel = monitoringViewModel) }
            composable("device-details/{deviceId}") { backStackEntry ->
                val deviceId = backStackEntry.arguments?.getString("deviceId").orEmpty()
                DeviceSnmpDetailsScreen(deviceId = deviceId, isArabic = isArabic, onBack = { navController.popBackStack() }, viewModel = monitoringViewModel)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(language = language, onLanguageChange = onLanguageChange, themeMode = themeMode, onThemeModeChange = onThemeModeChange)
            }
        }
    }
}
