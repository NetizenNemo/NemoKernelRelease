package com.nemo.updater.flash

import android.util.Log
import com.topjohnwu.superuser.Shell
import java.io.File

/**
 * Core flash engine.
 *
 * Based on AnyKernel3 principles:
 * 1. Detect boot partition (supports A/B slots)
 * 2. Backup current boot image
 * 3. Flash new boot image via dd
 * 4. Flash to inactive slot for A/B OTA
 */
object FlashEngine {

    private const val TAG = "FlashEngine"
    private const val NINC_DIR = "/data/local/tmp/ninc"

    /**
     * Initialize: create working directory
     */
    fun init(): Boolean {
        return exec("mkdir -p $NINC_DIR")
    }

    /**
     * Detect the boot partition on the device
     */
    fun detectBootPartition(): BootPartition? {
        // Try to find the boot partition
        val bootDevices = listOf(
            "by-name/boot", "by-name/init_boot",
            "mmcblk0p"   // fallback: try to enumerate
        )

        // Check if A/B device
        val abSlotProp = execGetOutput("getprop ro.build.ab_update")
        val isAb = abSlotProp == "true"

        // Get current slot
        val currentSlot = if (isAb) {
            execGetOutput("getprop ro.boot.slot_suffix")?.trim()?.takeIf { it.isNotEmpty() }
                ?: execGetOutput("getprop ro.boot.slot")?.trim()?.let { "_$it" }
        } else null

        // Search for the boot partition
        val slotSuffix = currentSlot ?: ""

        // Check common partition paths
        val paths = listOf(
            "/dev/block/bootdevice/by-name/boot$slotSuffix",
            "/dev/block/platform/bootdevice/by-name/boot$slotSuffix",
            "/dev/block/by-name/boot$slotSuffix",
            "/dev/block/bootdevice/by-name/init_boot$slotSuffix",
            "/dev/block/by-name/init_boot$slotSuffix",
        )

        for (path in paths) {
            if (execGetOutput("test -f $path && echo exists") == "exists") {
                Log.i(TAG, "Found boot partition: $path")
                return BootPartition(
                    device = "boot$slotSuffix",
                    blockPath = path,
                    isAb = isAb,
                    currentSlot = currentSlot,
                    slotSuffix = slotSuffix,
                )
            }
        }

        // Fallback: scan /dev/block
        val scanResult = execGetOutput("ls -d /dev/block/*/by-name/boot$slotSuffix 2>/dev/null")
        if (scanResult.isNotBlank()) {
            val path = scanResult.lines().firstOrNull()?.trim() ?: return null
            Log.i(TAG, "Found boot partition (scan): $path")
            return BootPartition(
                device = "boot$slotSuffix",
                blockPath = path,
                isAb = isAb,
                currentSlot = currentSlot,
                slotSuffix = slotSuffix,
            )
        }

        return null
    }

    /**
     * Backup the current boot partition
     */
    fun backupBoot(
        bootPartition: BootPartition,
        backupPath: String,
    ): FlashResult {
        Log.i(TAG, "Backing up boot to $backupPath")
        val cmd = "dd if=${bootPartition.blockPath} of=$backupPath bs=1M"
        val result = execWithResult(cmd)
        return if (result.isSuccess) {
            FlashResult(true, "Backup saved to $backupPath")
        } else {
            FlashResult(false, "Backup failed: ${result.err.joinToString("\n")}")
        }
    }

    /**
     * Flash a boot image to the device
     *
     * @param imagePath Path to the kernel image file on device
     * @param bootPartition Detected boot partition info
     * @param onLog Callback for real-time log output
     */
    fun flashBoot(
        imagePath: String,
        bootPartition: BootPartition,
        onLog: (String) -> Unit = {},
    ): FlashResult {
        Log.i(TAG, "Flashing $imagePath to ${bootPartition.blockPath}")

        onLog("Boot partition: ${bootPartition.blockPath}")
        onLog("")

        // Verify the image file exists
        val imgFile = File(imagePath)
        if (!imgFile.exists()) {
            return FlashResult(false, "Image file not found: $imagePath")
        }
        onLog("Image size: ${imgFile.length()} bytes")
        onLog("")

        // Ensure the target partition is writable
        onLog("Preparing partition...")
        exec("test -w ${bootPartition.blockPath} || (mount -o remount,rw / && mount -o remount,rw /system 2>/dev/null)")
        onLog("")

        // Flash using dd
        onLog("Flashing kernel...")
        val ddResult = execWithResult(
            "dd if=$imagePath of=${bootPartition.blockPath} bs=1M conv=fsync"
        )

        if (!ddResult.isSuccess) {
            val errMsg = ddResult.err.joinToString("\n")
            onLog("")
            onLog("FAILED: $errMsg")
            return FlashResult(false, "Flash failed: $errMsg")
        }

        onLog("")
        onLog("Kernel flashed successfully!")

        // Verify by reading back
        onLog("Verifying...")
        val verifyResult = execGetOutput("dd if=${bootPartition.blockPath} bs=1M count=1 2>/dev/null | md5sum")
        onLog("Verify hash: $verifyResult")
        onLog("")

        // For A/B devices, flash to inactive slot as well
        if (bootPartition.isAb && bootPartition.slotSuffix != null) {
            val otherSlot = if (bootPartition.slotSuffix == "_a") "_b" else "_a"
            val otherPath = bootPartition.blockPath.replace(bootPartition.slotSuffix, otherSlot)

            if (execGetOutput("test -f $otherPath && echo exists") == "exists") {
                onLog("A/B device detected, also flashing to slot $otherSlot...")
                val otherResult = execWithResult(
                    "dd if=$imagePath of=$otherPath bs=1M conv=fsync"
                )
                if (otherResult.isSuccess) {
                    onLog("Flashed to slot $otherSlot successfully")
                } else {
                    onLog("Warning: Failed to flash to slot $otherSlot")
                }
            }
        }

        return FlashResult(true, "Kernel installed successfully!", showReboot = true)
    }

    /**
     * Restore boot from a backup file
     */
    fun restoreBoot(
        backupPath: String,
        bootPartition: BootPartition,
        onLog: (String) -> Unit = {},
    ): FlashResult {
        val backupFile = File(backupPath)
        if (!backupFile.exists()) {
            return FlashResult(false, "Backup file not found: $backupPath")
        }

        onLog("Restoring boot from backup...")
        return flashBoot(backupPath, bootPartition, onLog)
    }

    /**
     * Copy a kernel image from content URI to a local temp file
     */
    fun copyToTemp(
        sourcePath: String,
        fileName: String = "kernel.img",
    ): String? {
        try {
            val tmpDir = File(NINC_DIR)
            tmpDir.mkdirs()
            val target = File(tmpDir, fileName)

            val srcFile = File(sourcePath)
            if (!srcFile.exists()) return null

            srcFile.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            target.setExecutable(true)
            return target.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy to temp", e)
            return null
        }
    }

    /**
     * Check if device has root access
     */
    fun hasRoot(): Boolean {
        return try {
            val result = execWithResult("id")
            result.isSuccess
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Reboot device
     */
    fun reboot(reason: String = "") {
        val cmd = if (reason.isEmpty()) "reboot" else "reboot $reason"
        exec(cmd)
    }

    // Private helpers

    private fun exec(cmd: String): Boolean {
        return Shell.cmd(cmd).exec().isSuccess
    }

    private fun execWithResult(cmd: String): Shell.Result {
        return Shell.cmd(cmd).exec()
    }

    private fun execGetOutput(cmd: String): String {
        return Shell.cmd(cmd).exec().out.joinToString("\n")
    }
}
