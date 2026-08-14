package com.wulisu.licenseoverlay.clipboard

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle

class ClipboardBridgeActivity : Activity() {
    private var consumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        window.decorView.alpha = 0f
    }

    override fun onResume() {
        super.onResume()
        window.decorView.postDelayed({ if (!consumed) finishWith(null) }, 900)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || consumed) return
        window.decorView.post { readClipboardOnce() }
    }

    private fun readClipboardOnce() {
        if (consumed) return
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = runCatching {
            manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        }.getOrNull()
        finishWith(text)
    }

    private fun finishWith(text: String?) {
        if (consumed) return
        consumed = true
        ClipboardBridge.deliver(text)
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        if (!consumed) ClipboardBridge.deliver(null)
        super.onDestroy()
    }
}
