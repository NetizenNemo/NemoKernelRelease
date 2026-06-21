package com.nemo.updater.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nemo.updater.flash.FlashEngine
import top.yukonga.miuix.kmp.basic.Card
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeScreen() {
    val hasRoot = remember { FlashEngine.hasRoot() }
    val bootPartition = remember { FlashEngine.detectBootPartition() }
    var showLog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            TopAppBar(title = "NINC")
        }

        item {
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "设备信息",
                        style = MiuixTheme.textStyles.title4,
                    )
                    Spacer(Modifier.height(8.dp))
                    DeviceInfoRow("Root 状态", if (hasRoot) "✅ 已获取" else "❌ 无 Root")
                    DeviceInfoRow("Boot 分区", bootPartition?.blockPath ?: "未检测到")
                    DeviceInfoRow("A/B 设备", if (bootPartition?.isAb == true) "是" else "否")
                    DeviceInfoRow("当前槽位", bootPartition?.slotSuffix ?: "-")
                }
            }
        }

        item {
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "快速操作",
                        style = MiuixTheme.textStyles.title4,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "内核信息与刷写操作将在「内核」页面完成",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
        )
    }
}
