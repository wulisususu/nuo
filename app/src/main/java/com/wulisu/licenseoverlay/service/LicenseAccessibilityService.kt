package com.wulisu.licenseoverlay.service

import android.accessibilityservice.AccessibilityService
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import com.wulisu.licenseoverlay.clipboard.ClipboardBridge
import com.wulisu.licenseoverlay.config.TargetAppRegistry
import com.wulisu.licenseoverlay.overlay.OverlayController

class LicenseAccessibilityService : AccessibilityService() {
    private lateinit var registry: TargetAppRegistry
    private lateinit var overlay: OverlayController

    override fun onServiceConnected() {
        super.onServiceConnected(); registry = TargetAppRegistry(this); overlay = OverlayController(this); INSTANCE = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::registry.isInitialized || !::overlay.isInitialized) return
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (packageName == this.packageName) { if (!ClipboardBridge.active) overlay.setTargetActive(false); return }
        if (packageName == currentImePackage()) return
        registry.pendingLearningLabel()?.let {
            val learnedLabel = registry.completeLearning(packageName)
            overlay.setTargetActive(true)
            learnedLabel?.let { label -> MainActivityEvents.notifyLearned(label, packageName) }
            return
        }
        overlay.setTargetActive(registry.isTarget(packageName))
    }

    override fun onInterrupt() = Unit
    override fun onDestroy() { if (::overlay.isInitialized) overlay.destroy(); INSTANCE = null; super.onDestroy() }
    private fun currentImePackage(): String? = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    companion object {
        @Volatile var INSTANCE: LicenseAccessibilityService? = null
            private set
    }
}

object MainActivityEvents {
    @Volatile var listener: ((String, String) -> Unit)? = null
    fun notifyLearned(label: String, packageName: String) { listener?.invoke(label, packageName) }
}
