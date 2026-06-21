package com.nemo.kernelflasher.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    HOME("首页", Icons.Outlined.Home, Icons.Outlined.Home),
    KERNEL("内核", Icons.Outlined.Storage, Icons.Outlined.Storage),
    SETTINGS("设置", Icons.Outlined.Settings, Icons.Outlined.Settings);
}
