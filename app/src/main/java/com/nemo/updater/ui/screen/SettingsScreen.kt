package com.nemo.updater.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nemo.updater.flash.BackupManager
import com.nemo.updater.ui.theme.AppThemeMode
import top.yukonga.miuix.kmp.basic.Card
import androidx.compose.foundation.lazy.LazyColumn
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsScreen() {
    var themeModeIndex by remember { mutableStateOf(0) }
    var autoBackup by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            TopAppBar(title = "设置")
        }

        // Flash settings
        item {
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("刷写设置", modifier = Modifier.padding(16.dp), style = MiuixTheme.textStyles.title4)
                SwitchPreference(
                    title = "自动备份",
                    summary = "刷写前自动备份当前内核",
                    checked = autoBackup,
                    onCheckedChange = { autoBackup = it },
                )
                ArrowPreference(
                    title = "Backup 管理",
                    summary = BackupManager.getBackupSize().let {
                        "${BackupManager.listBackups().size} 个备份 (${it / 1024 / 1024}MB)"
                    },
                    onClick = { },
                )
            }
        }

        // Appearance
        item {
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("外观", modifier = Modifier.padding(16.dp), style = MiuixTheme.textStyles.title4)
                OverlayDropdownPreference(
                    title = "主题模式",
                    summary = AppThemeMode.entries[themeModeIndex].name,
                    items = AppThemeMode.entries.map { it.name },
                    selectedIndex = themeModeIndex,
                    onSelectedIndexChange = { themeModeIndex = it },
                )
            }
        }

        // About
        item {
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("关于", modifier = Modifier.padding(16.dp), style = MiuixTheme.textStyles.title4)
                ArrowPreference(
                    title = "NINC",
                    summary = "v1.0.0 · 内核刷写工具",
                    onClick = { },
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
