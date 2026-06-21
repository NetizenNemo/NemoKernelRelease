package com.nemo.updater.flash

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages kernel backups and restores.
 * Backup location: /data/local/tmp/ninc/backups/ (root-accessible)
 */
object BackupManager {

    private const val BACKUP_DIR = "/data/local/tmp/ninc/backups"

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * Ensure backup dir exists (via root shell)
     */
    fun ensureDir(): Boolean {
        return FlashEngine.exec("mkdir -p $BACKUP_DIR && chmod 755 $BACKUP_DIR")
    }

    /**
     * Generate a backup file path with timestamp
     */
    fun generateBackupPath(): String {
        ensureDir()
        val timestamp = dateFormat.format(Date())
        return "$BACKUP_DIR/boot_backup_${timestamp}.img"
    }

    /**
     * List all available backups
     */
    fun listBackups(): List<String> {
        val out = FlashEngine.execGetOutput("ls -1 $BACKUP_DIR/*.img 2>/dev/null")
        return out.lines().filter { it.isNotBlank() }.sortedDescending()
    }

    /**
     * Get size of a backup file via root
     */
    fun getBackupSize(): Long {
        val sizes = FlashEngine.execGetOutput(
            "du -cb $BACKUP_DIR/*.img 2>/dev/null | tail -1 | cut -f1"
        )
        return sizes.toLongOrNull() ?: 0
    }

    /**
     * Delete a backup file
     */
    fun deleteBackup(path: String): Boolean {
        return FlashEngine.exec("rm -f '$path'")
    }
}
