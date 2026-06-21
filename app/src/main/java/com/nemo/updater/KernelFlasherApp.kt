package com.nemo.updater

import android.app.Application
import com.nemo.updater.flash.FlashEngine

class KernelFlasherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FlashEngine.initShell()
    }
}
