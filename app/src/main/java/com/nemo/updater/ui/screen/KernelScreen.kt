package com.nemo.updater.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nemo.updater.flash.BackupManager
import com.nemo.updater.flash.FlashEngine
import com.nemo.updater.flash.FlashResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.Card
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun KernelScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var log by remember { mutableStateOf("") }
    var isFlashing by remember { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var flashResult by remember { mutableStateOf<FlashResult?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedUri = it
            selectedFileName = it.lastPathSegment
            log = "已选择: ${it.lastPathSegment}\n"
            flashResult = null
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
    ) {
        item {
            TopAppBar(title = "内核")
        }

        // Local file selection
        item {
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("本地安装", style = MiuixTheme.textStyles.title4)
                    Spacer(Modifier.height(12.dp))

                    MiuixButton(
                        onClick = { filePicker.launch("application/zip") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isFlashing,
                    ) {
                        Text(
                            if (selectedUri != null) "已选: $selectedFileName"
                            else "选择 AK3 压缩包 (.zip)"
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    MiuixButton(
                        onClick = {
                            scope.launch {
                                isFlashing = true
                                flashResult = null
                                log = ""

                                val engine = FlashEngine
                                engine.ensureReady()

                                // Copy file to temp
                                val fileUri = selectedUri
                                if (fileUri == null) {
                                    log += "❌ 未选择文件\n"
                                    flashResult = FlashResult(false, "未选择文件")
                                    isFlashing = false
                                    return@launch
                                }

                                log += "📦 复制文件到临时目录...\n"
                                val tempPath = withContext(Dispatchers.IO) {
                                    engine.copyToTemp(context, fileUri)
                                }
                                if (tempPath == null) {
                                    log += "❌ 文件复制失败\n"
                                    flashResult = FlashResult(false, "文件复制失败")
                                    isFlashing = false
                                    return@launch
                                }
                                log += "✅ $tempPath\n\n"

                                // Detect boot partition
                                log += "🔍 检测 boot 分区...\n"
                                val bootPartition = withContext(Dispatchers.IO) {
                                    engine.detectBootPartition()
                                }
                                if (bootPartition == null) {
                                    log += "❌ 未检测到 boot 分区\n"
                                    flashResult = FlashResult(false, "未检测到 boot 分区")
                                    isFlashing = false
                                    return@launch
                                }
                                log += "📌 ${bootPartition.blockPath}\n"
                                log += if (bootPartition.isAb) "📌 A/B 设备, 槽位: ${bootPartition.slotSuffix}\n" else ""
                                log += "\n"

                                // Auto backup
                                log += "💾 备份当前内核...\n"
                                val backupPath = BackupManager.generateBackupPath()
                                val backupResult = withContext(Dispatchers.IO) {
                                    engine.backupBoot(bootPartition, backupPath)
                                }
                                if (backupResult.success) {
                                    log += "✅ 备份: $backupPath\n\n"
                                } else {
                                    log += "⚠️ 备份失败，继续刷写: ${backupResult.message}\n\n"
                                }

                                // Flash (auto-detects AK3 zip vs raw img)
                                log += "⚙️ 开始刷写...\n"
                                val result = withContext(Dispatchers.IO) {
                                    val sb = StringBuilder()
                                    engine.flashKernel(
                                        context = context,
                                        filePath = tempPath,
                                        bootPartition = bootPartition,
                                        onLog = { sb.append(it) },
                                    )
                                }

                                log += "\n${if (result.success) "✅ 刷写成功！" else "❌ 刷写失败"}\n"
                                log += result.message + "\n"
                                if (result.showReboot) {
                                    log += "\n🔄 建议重启设备以生效"
                                }
                                flashResult = result
                                isFlashing = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedUri != null && !isFlashing,
                    ) {
                        Text(if (isFlashing) "刷写中..." else "刷写内核")
                    }
                }
            }
        }

        // Progress & Log
        if (isFlashing || log.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("刷写日志", style = MiuixTheme.textStyles.title4)
                        if (isFlashing) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = log.ifEmpty { "等待操作..." },
                            style = MiuixTheme.textStyles.footnote2.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Online mode placeholder
        item {
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("在线安装（即将推出）", style = MiuixTheme.textStyles.title4)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "将从 GitHub Releases 获取最新内核",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}
