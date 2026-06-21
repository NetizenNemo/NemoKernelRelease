package com.nemo.updater.flash

/**
 * Boot partition info: device path and slot info
 */
data class BootPartition(
    val device: String,
    val blockPath: String,
    val isAb: Boolean,
    val currentSlot: String?,
    val slotSuffix: String?,
)

/**
 * Result of a flash operation
 */
data class FlashResult(
    val success: Boolean,
    val message: String,
    val showReboot: Boolean = false,
)
