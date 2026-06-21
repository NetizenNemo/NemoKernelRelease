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
     * Ensure root shell is available and working dir exists.
     */
    fun ensureReady(): Boolean {
        val su = execGetOutput("command -v su")
        if (su.isBlank()) return false
        return exec("mkdir -p $NINC_DIR && chmod 755 $NINC_DIR")
    }

    /**
     * Detect the boot partition on the device
     */
    fun detectBootPartition(): BootPartition? {
        val abSlotProp = execGetOutput("getprop ro.build.ab_update")
        val isAb = abSlotProp == "true"

        val currentSlot = if (isAb) {
            (execGetOutput("getprop ro.boot.slot_suffix").trim().takeIf { it.isNotEmpty() }
                ?: execGetOutput("getprop ro.boot.slot").trim().let { if (it.isNotEmpty()) "_$it" else "" })
        } else null

        val slotSuffix = currentSlot ?: ""

        val paths = listOf(
            "/dev/block/bootdevice/by-name/boot$slotSuffix",
            "/dev/block/by-name/boot$slotSuffix",
            "/dev/block/bootdevice/by-name/init_boot$slotSuffix",
            "/dev/block/by-name/init_boot$slotSuffix",
        )

        for (path in paths) {
            if (execGetOutput("test -e $path && echo 1") == "1") {
                Log.i(TAG, "Found boot partition: $path")
                return BootPartition("boot$slotSuffix", path, isAb, currentSlot, slotSuffix)
            }
        }

        val scanResult = execGetOutput("ls -d /dev/block/*/by-name/boot$slotSuffix 2>/dev/null")
        if (scanResult.isNotBlank()) {
            val path = scanResult.lines().first().trim()
            Log.i(TAG, "Found boot partition (scan): $path")
            return BootPartition("boot$slotSuffix", path, isAb, currentSlot, slotSuffix)
        }

        return null
    }

    /**
     * Backup current boot partition via dd
     */
    fun backupBoot(bootPartition: BootPartition, backupPath: String): FlashResult {
        Log.i(TAG, "Backing up boot to $backupPath")
        val (code, out, err) = suExec("dd if=${bootPartition.blockPath} of=$backupPath bs=1048576")
        return if (code == 0) {
            FlashResult(true, "Backup saved to $backupPath")
        } else {
            FlashResult(false, "Backup failed: $err")
        }
    }

    /**
     * Flash a kernel — auto-detects AK3 zip vs raw .img by file extension.
     */
    fun flashKernel(
        filePath: String,
        bootPartition: BootPartition,
        onLog: (String) -> Unit = {},
    ): FlashResult {
        val file = File(filePath)
        if (!file.exists()) return FlashResult(false, "文件不存在: $filePath")
        ensureReady()

        return if (file.name.endsWith(".zip", ignoreCase = true)) {
            flashAk3Zip(filePath, bootPartition, onLog)
        } else {
            flashRawImg(filePath, bootPartition, onLog)
        }
    }

    /**
     * Flash via AnyKernel3 zip
     */
    private fun flashAk3Zip(
        zipPath: String,
        bootPartition: BootPartition,
        onLog: (String) -> Unit,
    ): FlashResult {
        val akDir = "$NINC_DIR/ak3"
        exec("rm -rf $akDir && mkdir -p $akDir")

        onLog("📦 解压 AK3 压缩包...\n")
        val extractOk = try {
            val zipFile = java.util.zip.ZipFile(zipPath)
            var count = 0
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val f = File(akDir, entry.name)
                if (entry.isDirectory) { f.mkdirs() } else {
                    f.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    count++
                }
            }
            zipFile.close()
            onLog("✅ 解压完成: $count 个文件\n")
            true
        } catch (e: Exception) {
            onLog("❌ 解压失败: ${e.message}\n")
            return FlashResult(false, "解压失败: ${e.message}")
        }
        if (!extractOk) return FlashResult(false, "解压失败")

        exec("chmod -R 755 $akDir/tools/ 2>/dev/null")

        // Verify
        if (execGetOutput("test -f $akDir/tools/ak3-core.sh && echo 1") != "1") {
            return FlashResult(false, "无效 AK3: 缺少 tools/ak3-core.sh")
        }

        onLog("⚙️ 执行 AK3 刷写...\n")

        // Write boot script directly (avoid shell heredoc escaping issues)
        val scriptFile = File("$NINC_DIR/ninc-boot.sh")
        scriptFile.writeText("""
#!/sbin/sh
export BOOTMODE=true
export OUTFD=1
export AKHOME=$akDir
export ANDROID_ROOT=/system
cd $akDir || exit 1
chmod -R 755 tools/ 2>/dev/null
. tools/ak3-core.sh || exit 1
[ -f anykernel.sh ] && . anykernel.sh
BLOCK=${bootPartition.blockPath}
IS_SLOT_DEVICE=${if (bootPartition.isAb) "1" else "0"}
echo "Target: ${'$'}BLOCK"
echo ""
dump_boot
echo "dump_boot OK"
write_boot
echo "write_boot OK"
exit 0
""".trimIndent())
        exec("chmod 755 ${scriptFile.absolutePath}")

        // Execute via su -c
        val (code, stdout, stderr) = suExecWithLog("sh ${scriptFile.absolutePath}", onLog)

        exec("rm -rf $akDir ${scriptFile.absolutePath}")

        return if (code == 0) {
            FlashResult(true, "AK3 刷写成功！建议重启", showReboot = true)
        } else {
            FlashResult(false, "AK3 刷写失败 (code=$code): $stderr")
        }
    }

    /**
     * Flash via direct dd for raw .img files
     */
    private fun flashRawImg(
        imgPath: String,
        bootPartition: BootPartition,
        onLog: (String) -> Unit,
    ): FlashResult {
        onLog("📁 镜像大小: ${File(imgPath).length()} bytes\n")
        onLog("📌 目标: ${bootPartition.blockPath}\n\n")

        onLog("⚙️ 刷写中...\n")
        val (code, _, err) = suExec("dd if=$imgPath of=${bootPartition.blockPath} bs=1048576 conv=fsync")

        if (code != 0) {
            onLog("❌ 失败: $err\n")
            return FlashResult(false, "dd 失败: $err")
        }
        onLog("✅ 刷写成功！\n")

        // A/B
        if (bootPartition.isAb && bootPartition.slotSuffix != null) {
            val other = if (bootPartition.slotSuffix == "_a") "_b" else "_a"
            val otherPath = bootPartition.blockPath.replace(bootPartition.slotSuffix, other)
            if (execGetOutput("test -e $otherPath && echo 1") == "1") {
                onLog("📌 同步刷写槽位 $other...\n")
                suExec("dd if=$imgPath of=$otherPath bs=1048576 conv=fsync")
                onLog("✅ 完成\n")
            }
        }

        return FlashResult(true, "刷写成功！建议重启", showReboot = true)
    }

    fun restoreBoot(backupPath: String, bootPartition: BootPartition, onLog: (String) -> Unit = {}): FlashResult {
        if (execGetOutput("test -f $backupPath && echo 1") != "1")
            return FlashResult(false, "备份不存在: $backupPath")
        onLog("♻️ 恢复备份...\n")
        return flashRawImg(backupPath, bootPartition, onLog)
    }

    fun copyToTemp(context: android.content.Context, uri: android.net.Uri, fileName: String = "kernel.zip"): String? {
        return try {
            val cacheDir = File(context.cacheDir, "ninc").also { it.mkdirs() }
            val target = File(cacheDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { i ->
                target.outputStream().use { o -> i.copyTo(o) }
            } ?: File(uri.path ?: return null).inputStream().use { it.copyTo(target.outputStream()) }

            ensureReady()
            exec("cp -f ${target.absolutePath} $NINC_DIR/$fileName && chmod 644 $NINC_DIR/$fileName")
            "$NINC_DIR/$fileName"
        } catch (e: Exception) {
            Log.e(TAG, "copyToTemp failed", e)
            null
        }
    }

    fun hasRoot(): Boolean = try {
        execGetOutput("id -u") == "0"
    } catch (_: Exception) { false }

    fun reboot(reason: String = "") {
        suExec(if (reason.isEmpty()) "reboot" else "reboot $reason")
    }

    // --- Core shell execution with explicit su ---

    /**
     * Execute a command as root via su -c, return (code, stdout, stderr)
     */
    private fun suExec(cmd: String): Triple<Int, String, String> {
        // Write exit code to a temp file to capture it reliably
        val result = Shell.cmd("su -c '$cmd'; echo EXITCODE:${'$'}? | tee /data/local/tmp/ninc_exit.${'$'}$").exec()
        val all = result.out.joinToString("\n").trim()
        val codeLine = all.lines().lastOrNull { it.startsWith("EXITCODE:") }
        val code = codeLine?.removePrefix("EXITCODE:")?.toIntOrNull() ?: result.code
        return Triple(code, all, result.err.joinToString("\n"))
    }

    /**
     * Execute command with streaming log output
     */
    private fun suExecWithLog(cmd: String, onLog: (String) -> Unit): Triple<Int, String, String> {
        val safeCmd = cmd.replace("'", "'\\''")
        val fullCmd = "su -c '$safeCmd'; echo EXITCODE:${'$'}?"
        val outCallback = object : com.topjohnwu.superuser.CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                if (s != null && !s.startsWith("EXITCODE:")) onLog("$s\n")
            }
        }
        val errCallback = object : com.topjohnwu.superuser.CallbackList<String?>() {
            override fun onAddElement(s: String?) { s?.let { onLog("⚠️ $it\n") } }
        }
        val result = Shell.cmd(fullCmd).to(outCallback, errCallback).exec()
        val codeLine = result.out.lastOrNull { it.startsWith("EXITCODE:") }
        val code = codeLine?.removePrefix("EXITCODE:")?.toIntOrNull() ?: result.code
        val stderr = result.err.joinToString("\n")
        return Triple(code, "", stderr)
    }

    // --- Simple exec helpers ---

    fun exec(cmd: String): Boolean = Shell.cmd("su -c '$cmd'").exec().isSuccess

    fun execGetOutput(cmd: String): String =
        Shell.cmd("su -c '$cmd'").exec().out.joinToString("\n").trim()

    private fun execWithResult(cmd: String): Shell.Result = Shell.cmd("su -c '$cmd'").exec()
}
