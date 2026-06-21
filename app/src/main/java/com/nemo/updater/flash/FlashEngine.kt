package com.nemo.updater.flash

import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * Core flash engine supporting both AnyKernel3 (.zip) and raw boot images (.img).
 */
object FlashEngine {

    private const val TAG = "FlashEngine"
    private const val NINC_DIR = "/data/local/tmp/ninc"

    /**
     * Initialize root shell once. Call this early (e.g. from Application.onCreate).
     */
    fun initShell() {
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
    }

    /**
     * Ensure working dir exists. All shell commands run via libsu (already root).
     */
    fun ensureReady(): Boolean {
        return su("mkdir -p $NINC_DIR && chmod 755 $NINC_DIR")
    }

    /**
     * Detect the boot partition on the device
     */
    fun detectBootPartition(): BootPartition? {
        val isAb = suOut("getprop ro.build.ab_update") == "true"

        val currentSlot = if (isAb) {
            (suOut("getprop ro.boot.slot_suffix").trim().takeIf { it.isNotEmpty() }
                ?: suOut("getprop ro.boot.slot").trim().let { if (it.isNotEmpty()) "_$it" else "" })
        } else null

        val slotSuffix = currentSlot ?: ""

        for (path in listOf(
            "/dev/block/bootdevice/by-name/boot$slotSuffix",
            "/dev/block/by-name/boot$slotSuffix",
            "/dev/block/bootdevice/by-name/init_boot$slotSuffix",
            "/dev/block/by-name/init_boot$slotSuffix",
        )) {
            if (suOut("test -e $path && echo 1") == "1") {
                Log.i(TAG, "Found boot partition: $path")
                return BootPartition("boot$slotSuffix", path, isAb, currentSlot, slotSuffix)
            }
        }

        val scanResult = suOut("ls -d /dev/block/*/by-name/boot$slotSuffix 2>/dev/null")
        if (scanResult.isNotBlank()) {
            val path = scanResult.lines().first().trim()
            Log.i(TAG, "Found boot partition (scan): $path")
            return BootPartition("boot$slotSuffix", path, isAb, currentSlot, slotSuffix)
        }
        return null
    }

    fun backupBoot(bootPartition: BootPartition, backupPath: String): FlashResult {
        Log.i(TAG, "Backing up boot to $backupPath")
        val r = suResult("dd if=${bootPartition.blockPath} of=$backupPath bs=1048576")
        return if (r.isSuccess) FlashResult(true, "Backup saved to $backupPath")
        else FlashResult(false, "Backup failed: ${r.err.joinToString("\n")}")
    }

    fun flashKernel(filePath: String, bootPartition: BootPartition, onLog: (String) -> Unit = {}): FlashResult {
        if (!File(filePath).exists()) return FlashResult(false, "文件不存在: $filePath")
        ensureReady()
        return if (filePath.endsWith(".zip", ignoreCase = true))
            flashAk3Zip(filePath, bootPartition, onLog)
        else
            flashRawImg(filePath, bootPartition, onLog)
    }

    /**
     * AK3 flash: extract update-binary from zip, run it with sh via libsu.
     */
    private fun flashAk3Zip(zipPath: String, bootPartition: BootPartition, onLog: (String) -> Unit): FlashResult {
        val workDir = "$NINC_DIR/ak3"
        su("rm -rf $workDir && mkdir -p $workDir")

        onLog("📦 提取 update-binary...\n")
        try {
            val zip = java.util.zip.ZipFile(zipPath)
            val entry = zip.getEntry("META-INF/com/google/android/update-binary")
                ?: return FlashResult(false, "AK3 包缺少 update-binary")
            val ubFile = File(workDir, "update-binary")
            zip.getInputStream(entry).use { i -> ubFile.outputStream().use { o -> i.copyTo(o) } }
            zip.close()
            su("chmod 755 $workDir/update-binary")
        } catch (e: Exception) {
            return FlashResult(false, "提取失败: ${e.message}")
        }

        onLog("⚙️ 执行 AK3 update-binary...\n")
        // update-binary args: <fd> <OUTFD> <ZIPFILE>
        // In boot mode, update-binary handles everything: dump_boot, patch, write_boot
        val cmd = "cd $workDir && sh update-binary 3 1 '$zipPath'"
        val (code, stderr) = suStream(cmd, onLog)

        su("rm -rf $workDir")

        return if (code == 0) FlashResult(true, "AK3 刷写成功！建议重启", showReboot = true)
        else FlashResult(false, "AK3 失败 (code=$code): $stderr")
    }

    private fun flashRawImg(imgPath: String, bootPartition: BootPartition, onLog: (String) -> Unit): FlashResult {
        onLog("📁 镜像: ${File(imgPath).length()} bytes\n📌 目标: ${bootPartition.blockPath}\n\n")
        onLog("⚙️ 刷写中...\n")
        val r = suResult("dd if=$imgPath of=${bootPartition.blockPath} bs=1048576 conv=fsync")
        if (!r.isSuccess) {
            onLog("❌ ${r.err.joinToString("\n")}\n")
            return FlashResult(false, "dd 失败: ${r.err.joinToString("\n")}")
        }
        onLog("✅ 成功！\n")

        // A/B
        if (bootPartition.isAb && bootPartition.slotSuffix != null) {
            val other = if (bootPartition.slotSuffix == "_a") "_b" else "_a"
            val otherPath = bootPartition.blockPath.replace(bootPartition.slotSuffix, other)
            if (suOut("test -e $otherPath && echo 1") == "1") {
                onLog("📌 同步刷写 $other...\n")
                suResult("dd if=$imgPath of=$otherPath bs=1048576 conv=fsync")
                onLog("✅ 完成\n")
            }
        }
        return FlashResult(true, "刷写成功！建议重启", showReboot = true)
    }

    fun restoreBoot(backupPath: String, bootPartition: BootPartition, onLog: (String) -> Unit = {}): FlashResult {
        if (suOut("test -f $backupPath && echo 1") != "1")
            return FlashResult(false, "备份不存在: $backupPath")
        onLog("♻️ 恢复...\n")
        return flashRawImg(backupPath, bootPartition, onLog)
    }

    fun copyToTemp(context: android.content.Context, uri: android.net.Uri, fileName: String = "kernel.zip"): String? {
        return try {
            val f = File(context.cacheDir, "ninc/$fileName").also { it.parentFile?.mkdirs() }
            context.contentResolver.openInputStream(uri)?.use { i ->
                f.outputStream().use { o -> i.copyTo(o) }
            } ?: File(uri.path ?: return null).inputStream().use { it.copyTo(f.outputStream()) }
            ensureReady()
            su("cp -f ${f.absolutePath} $NINC_DIR/$fileName && chmod 644 $NINC_DIR/$fileName")
            "$NINC_DIR/$fileName"
        } catch (e: Exception) {
            Log.e(TAG, "copyToTemp failed", e); null
        }
    }

    fun hasRoot(): Boolean = try { suOut("id -u") == "0" } catch (_: Exception) { false }
    fun reboot(reason: String = "") { su(if (reason.isEmpty()) "reboot" else "reboot $reason") }

    // --- libsu native helpers (no nested su -c) ---

    /** Run command via libsu root shell, return success */
    fun su(cmd: String): Boolean = Shell.cmd(cmd).exec().isSuccess

    /** Run command, return stdout */
    fun suOut(cmd: String): String = Shell.cmd(cmd).exec().out.joinToString("\n").trim()

    /** Run command, return full result */
    private fun suResult(cmd: String): Shell.Result = Shell.cmd(cmd).exec()

    /** Run command with streaming output */
    private fun suStream(cmd: String, onLog: (String) -> Unit): Pair<Int, String> {
        val outCb = object : com.topjohnwu.superuser.CallbackList<String?>() {
            override fun onAddElement(s: String?) { s?.let { onLog("$it\n") } }
        }
        val errCb = object : com.topjohnwu.superuser.CallbackList<String?>() {
            override fun onAddElement(s: String?) { s?.let { onLog("⚠️ $it\n") } }
        }
        val r = Shell.cmd(cmd).to(outCb, errCb).exec()
        return Pair(r.code, r.err.joinToString("\n"))
    }
}
