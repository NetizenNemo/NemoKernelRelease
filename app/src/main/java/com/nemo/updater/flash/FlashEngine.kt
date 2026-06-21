package com.nemo.updater.flash

import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * Core flash engine supporting both AnyKernel3 (.zip) and raw boot images (.img).
 *
 * AnyKernel3 flow:
 *   Extract zip → source ak3-core.sh → dump_boot (split current boot) →
 *   replace kernel (Image/zImage) → write_boot (repack + dd flash)
 *
 * Raw .img flow:
 *   dd if=new.img of=/dev/block/boot
 */
object FlashEngine {

    private const val TAG = "FlashEngine"
    private const val NINC_DIR = "/data/local/tmp/ninc"

    /**
     * Initialize working directory
     */
    fun init(): Boolean {
        return exec("rm -rf $NINC_DIR && mkdir -p $NINC_DIR")
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
            if (execGetOutput("test -e $path && echo exists") == "exists") {
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
        val result = execWithResult("dd if=${bootPartition.blockPath} of=$backupPath bs=1048576")
        return if (result.isSuccess) {
            FlashResult(true, "Backup saved to $backupPath")
        } else {
            FlashResult(false, "Backup failed: ${result.err.joinToString("\n")}")
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

        return if (file.name.endsWith(".zip", ignoreCase = true)) {
            flashAk3Zip(filePath, bootPartition, onLog)
        } else if (file.name.endsWith(".img", ignoreCase = true)) {
            flashRawImg(filePath, bootPartition, onLog)
        } else {
            // Try as AK3 zip first if extension unknown
            flashAk3Zip(filePath, bootPartition, onLog)
        }
    }

    /**
     * Flash via AnyKernel3 zip: extract → split_boot → write_boot
     */
    private fun flashAk3Zip(
        zipPath: String,
        bootPartition: BootPartition,
        onLog: (String) -> Unit,
    ): FlashResult {
        val akDir = "$NINC_DIR/ak3"
        init()

        onLog("📦 解析 AK3 压缩包...\n")

        // Unzip
        val unzipResult = execWithResult(
            "unzip -o $zipPath -d $akDir 2>/dev/null || " +
            "busybox unzip -o $zipPath -d $akDir 2>/dev/null"
        )
        if (!unzipResult.isSuccess) {
            return FlashResult(false, "解压 AK3 失败: ${unzipResult.err.joinToString("\n")}")
        }

        // Verify key files exist
        val coreScript = "$akDir/tools/ak3-core.sh"
        if (execGetOutput("test -f $coreScript && echo 1") != "1") {
            return FlashResult(false, "无效的 AK3 包: 缺少 tools/ak3-core.sh")
        }

        onLog("✅ 解压完成\n")
        onLog("⚙️ 执行 AK3 刷写脚本...\n")

        // Set executable permissions
        exec("chmod -R 755 $akDir/tools/")

        // Build the AK3 boot script
        // ak3-core.sh expects BOOTMODE=true, OUTFD set
        // It reads BLOCK from anykernel.sh, dumps boot, replaces Image, repacks, writes
        val bootScript = """
            export BOOTMODE=true
            export OUTFD=/proc/self/fd/1
            export AKHOME=$akDir
            export ANDROID_ROOT=/system
            cd $akDir

            # Source ak3-core and run
            . tools/ak3-core.sh

            # Override BLOCK from detected partition
            BLOCK=${bootPartition.blockPath}
            IS_SLOT_DEVICE=${if (bootPartition.isAb) "1" else "0"}

            ui_print "NINC: Starting AK3 flash..."
            dump_boot
            ui_print "NINC: Boot image dumped, replacing kernel..."
            write_boot
            ui_print "NINC: Flash complete!"
        """.trimIndent()

        // Write the boot script and execute
        exec("cat > $NINC_DIR/ninc-boot.sh << 'NINCSCRIPT'\n$bootScript\nNINCSCRIPT")
        exec("chmod 755 $NINC_DIR/ninc-boot.sh")

        // Run with root shell, streaming output
        val result = execWithStream("sh $NINC_DIR/ninc-boot.sh", onLog)

        // Clean up
        exec("rm -rf $akDir $NINC_DIR/ninc-boot.sh")

        return if (result.isSuccess) {
            FlashResult(true, "AK3 刷写成功！建议重启", showReboot = true)
        } else {
            FlashResult(false, "AK3 刷写失败: ${result.err.joinToString("\n")}")
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
        init()
        val file = File(imgPath)
        onLog("📁 原始 boot 镜像: ${file.length()} bytes\n")
        onLog("📌 目标: ${bootPartition.blockPath}\n\n")

        // Remount rw
        exec("mount -o remount,rw / 2>/dev/null")

        onLog("⚙️ 刷写中...\n")
        val ddResult = execWithResult("dd if=$imgPath of=${bootPartition.blockPath} bs=1048576 conv=fsync")

        if (!ddResult.isSuccess) {
            onLog("❌ 刷写失败: ${ddResult.err.joinToString("\n")}\n")
            return FlashResult(false, "dd 刷写失败: ${ddResult.err.joinToString("\n")}")
        }

        onLog("✅ 刷写成功！\n")

        // A/B: also flash to inactive slot
        if (bootPartition.isAb && bootPartition.slotSuffix != null) {
            val otherSlot = if (bootPartition.slotSuffix == "_a") "_b" else "_a"
            val otherPath = bootPartition.blockPath.replace(bootPartition.slotSuffix, otherSlot)

            if (execGetOutput("test -e $otherPath && echo 1") == "1") {
                onLog("📌 双槽位设备，同步刷写 $otherSlot...\n")
                execWithResult("dd if=$imgPath of=$otherPath bs=1048576 conv=fsync")
                onLog("✅ 槽位 $otherSlot 完成\n")
            }
        }

        return FlashResult(true, "刷写成功！建议重启", showReboot = true)
    }

    /**
     * Restore boot from a backup .img file
     */
    fun restoreBoot(backupPath: String, bootPartition: BootPartition, onLog: (String) -> Unit = {}): FlashResult {
        val file = File(backupPath)
        if (!file.exists()) return FlashResult(false, "备份文件不存在: $backupPath")
        onLog("♻️ 从备份恢复...\n")
        return flashRawImg(backupPath, bootPartition, onLog)
    }

    /**
     * Copy a file or content URI to temp dir.
     * Uses app cache dir for reliable write access, then copies to work dir via root.
     */
    fun copyToTemp(context: android.content.Context, uri: android.net.Uri, fileName: String = "kernel.zip"): String? {
        return try {
            // Use app cache dir (always writable by the app process)
            val cacheDir = File(context.cacheDir, "ninc")
            cacheDir.mkdirs()
            val target = File(cacheDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: run {
                // Fallback: try as file path
                File(uri.path ?: return null).inputStream().use { it.copyTo(target.outputStream()) }
            }

            // Copy to root-accessible working dir via shell
            init()
            exec("cp -f ${target.absolutePath} $NINC_DIR/$fileName")
            exec("chmod 644 $NINC_DIR/$fileName")

            "$NINC_DIR/$fileName"
        } catch (e: Exception) {
            Log.e(TAG, "copyToTemp failed", e)
            null
        }
    }

    fun hasRoot(): Boolean = try {
        execWithResult("id -u").out.firstOrNull() == "0"
    } catch (_: Exception) { false }

    fun reboot(reason: String = "") {
        exec(if (reason.isEmpty()) "reboot" else "reboot $reason")
    }

    // --- Shell helpers ---

    private fun exec(cmd: String): Boolean = Shell.cmd(cmd).exec().isSuccess

    private fun execWithResult(cmd: String): Shell.Result = Shell.cmd(cmd).exec()

    private fun execGetOutput(cmd: String): String =
        Shell.cmd(cmd).exec().out.joinToString("\n").trim()

    /**
     * Execute command with real-time log streaming
     */
    private fun execWithStream(cmd: String, onLog: (String) -> Unit): Shell.Result {
        val outCallback = object : com.topjohnwu.superuser.CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                s?.let { onLog("$it\n") }
            }
        }
        val errCallback = object : com.topjohnwu.superuser.CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                s?.let { onLog("⚠️ $it\n") }
            }
        }
        return Shell.cmd(cmd).to(outCallback, errCallback).exec()
    }
}
