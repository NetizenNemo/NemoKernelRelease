package com.nemo.updater.flash

import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages kernel backups and restores.
 * Backup location: /sdcard/NINC/backups/
 */
object BackupManager {

    private val backupDir: File
        get() = File(
            Environment.getExternalStorageDirectory(),
            "NINC/backups"
        ).also { it.mkdirs() }

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Generate a backup file path with timestamp
     */
    fun generateBackupPath(): String {
        val timestamp = dateFormat.format(Date())
        return File(backupDir, "boot_backup_${timestamp}.img").absolutePath
    }

    /**
     * List all available backups
     */
    fun listBackups(): List<File> {
        return backupDir.listFiles { f -> f.name.endsWith(".img") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Get backup file by index (0 = newest)
     */
    fun getBackup(index: Int = 0): File? {
        val backups = listBackups()
        return backups.getOrNull(index)
    }

    /**
     * Delete a backup file
     */
    fun deleteBackup(file: File): Boolean {
        return file.delete()
    }

    /**
     * Get total backup size
     */
    fun getBackupSize(): Long {
        return listBackups().sumOf { it.length() }
    }
}
