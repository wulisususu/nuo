package com.wulisu.licenseoverlay.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import com.wulisu.licenseoverlay.overlay.OverlayController

class LicenseAccessibilityService : AccessibilityService() {
    private lateinit var overlay: OverlayController
    private lateinit var powerManager: PowerManager
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!::overlay.isInitialized) return
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> overlay.setScreenActive(true)
                Intent.ACTION_SCREEN_OFF -> overlay.setScreenActive(false)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        powerManager = getSystemService(PowerManager::class.java)
        overlay = OverlayController(this)
        INSTANCE = this

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(screenReceiver, filter)
        }
        receiverRegistered = true
        overlay.setScreenActive(powerManager.isInteractive)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (::overlay.isInitialized && ::powerManager.isInitialized) {
            overlay.setScreenActive(powerManager.isInteractive)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (receiverRegistered) runCatching { unregisterReceiver(screenReceiver) }
        receiverRegistered = false
        if (::overlay.isInitialized) overlay.destroy()
        INSTANCE = null
        super.onDestroy()
    }

    companion object {
        @Volatile var INSTANCE: LicenseAccessibilityService? = null
            private set
    }
}
