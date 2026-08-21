package org.immortalwrt.manager.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Main : Screen("main")
}

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Dashboard : BottomNavItem("dashboard", "仪表盘", Icons.Default.Dashboard)
    object Clients : BottomNavItem("clients", "终端设备", Icons.Default.Devices)
    object Wireless : BottomNavItem("wireless", "无线网络", Icons.Default.Wifi)
    object Tools : BottomNavItem("tools", "工具插件", Icons.Default.Build)
    object Settings : BottomNavItem("settings", "设置网络", Icons.Default.Settings)
}

