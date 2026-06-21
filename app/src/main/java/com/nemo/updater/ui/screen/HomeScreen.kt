package com.nemo.updater.ui.screen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nemo.updater.flash.FlashEngine
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.luminance

@Composable
fun HomeScreen() {
    val hasRoot = remember { FlashEngine.hasRoot() }
    val bootPartition = remember { FlashEngine.detectBootPartition() }
    val kernelVersion = remember {
        try { Runtime.getRuntime().exec("uname -r").inputStream.bufferedReader().readText().trim() }
        catch (_: Exception) { "未知" }
    }

    val statusContainer = if (hasRoot) Color(0xFF1A3825) else MiuixTheme.colorScheme.errorContainer
    val statusContent = if (hasRoot) Color(0xFF36D167) else MiuixTheme.colorScheme.onErrorContainer
    val statusTitle = if (hasRoot) "已激活" else "未获取 ROOT 权限"
    val statusDesc = if (hasRoot) "内核刷写功能正常" else "请检查设备 Root 状态"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp),
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("NINC", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface)
            Text("NeoCenter 内核管理器", fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.height(16.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.defaultColors(color = statusContainer),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-4).dp)) {
                        Text(
                            text = if (hasRoot) "✓" else "✗",
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusContent.copy(alpha = 0.15f),
                        )
                    }
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(statusTitle, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = statusContent)
                        Spacer(Modifier.height(2.dp))
                        Text(statusDesc, fontSize = 14.sp, color = statusContent.copy(alpha = 0.7f))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("系统信息", fontSize = 17.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(6.dp))
                    InfoItem("内核版本", kernelVersion)
                    InfoItem("Boot 分区", bootPartition?.blockPath ?: "未检测到")
                    InfoItem("A/B 设备", if (bootPartition?.isAb == true) "是" else "否")
                    InfoItem("当前槽位", bootPartition?.slotSuffix ?: "-")
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.padding(top = 8.dp))
    Text(value, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurface, modifier = Modifier.padding(bottom = 4.dp))
}
