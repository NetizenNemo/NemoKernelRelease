package com.nemo.updater.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Update

enum class NavTab(
    val label: String,
    val icon: ImageVector,
) {
    HOME("首页", MiuixIcons.All),
    KERNEL("内核", MiuixIcons.Update),
    SETTINGS("设置", MiuixIcons.Settings);
}
